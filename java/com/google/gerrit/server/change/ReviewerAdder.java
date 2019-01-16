// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class ReviewerAdder {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  public enum FailureBehavior {
    FAIL,
    IGNORE;
  }

  private enum FailureType {
    NOT_FOUND,
    OTHER;
  }

  // TODO(dborowitz): Subclassing is not the right way to do this. We should instead use an internal
  // type in the public interfaces of ReviewerAdder, rather than passing around the REST API type
  // internally.
  public static class InternalAddReviewerInput extends AddReviewerInput {
    /**
     * Behavior when identifying reviewers fails for any reason <em>besides</em> the input not
     * resolving to an account/group/email.
     */
    public FailureBehavior otherFailureBehavior = FailureBehavior.FAIL;
  }

  public static InternalAddReviewerInput newAddReviewerInput(
      Account.Id reviewer, ReviewerState state, NotifyHandling notify) {
    // AccountResolver always resolves by ID if the input string is numeric.
    return newAddReviewerInput(reviewer.toString(), state, notify);
  }

  public static InternalAddReviewerInput newAddReviewerInput(
      String reviewer, ReviewerState state, NotifyHandling notify) {
    InternalAddReviewerInput in = new InternalAddReviewerInput();
    in.reviewer = reviewer;
    in.state = state;
    in.notify = notify;
    return in;
  }

  public static Optional<InternalAddReviewerInput> newAddReviewerInputFromCommitIdentity(
      Change change, @Nullable Account.Id accountId, NotifyHandling notify) {
    if (accountId == null || accountId.equals(change.getOwner())) {
      // If git ident couldn't be resolved to a user, or if it's not forged, do nothing.
      return Optional.empty();
    }

    InternalAddReviewerInput in = new InternalAddReviewerInput();
    in.reviewer = accountId.toString();
    in.state = REVIEWER;
    in.notify = notify;
    in.otherFailureBehavior = FailureBehavior.IGNORE;
    return Optional.of(in);
  }

  private final AccountResolver accountResolver;
  private final PermissionBackend permissionBackend;
  private final GroupResolver groupResolver;
  private final GroupMembers groupMembers;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Config cfg;
  private final ReviewerJson json;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousProvider;
  private final AddReviewersOp.Factory addReviewersOpFactory;
  private final OutgoingEmailValidator validator;

  @Inject
  ReviewerAdder(
      AccountResolver accountResolver,
      PermissionBackend permissionBackend,
      GroupResolver groupResolver,
      GroupMembers groupMembers,
      AccountLoader.Factory accountLoaderFactory,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider,
      AddReviewersOp.Factory addReviewersOpFactory,
      OutgoingEmailValidator validator) {
    this.accountResolver = accountResolver;
    this.permissionBackend = permissionBackend;
    this.groupResolver = groupResolver;
    this.groupMembers = groupMembers;
    this.accountLoaderFactory = accountLoaderFactory;
    this.cfg = cfg;
    this.json = json;
    this.projectCache = projectCache;
    this.anonymousProvider = anonymousProvider;
    this.addReviewersOpFactory = addReviewersOpFactory;
    this.validator = validator;
  }

  /**
   * Prepare application of a single {@link AddReviewerInput}.
   *
   * @param notes change notes.
   * @param user user performing the reviewer addition.
   * @param input input describing user or group to add as a reviewer.
   * @param allowGroup whether to allow
   * @return handle describing the addition operation. If the {@code op} field is present, this
   *     operation may be added to a {@code BatchUpdate}. Otherwise, the {@code error} field
   *     contains information about an error that occurred
   * @throws StorageException
   * @throws IOException
   * @throws PermissionBackendException
   * @throws ConfigInvalidException
   */
  public ReviewerAddition prepare(
      ChangeNotes notes, CurrentUser user, AddReviewerInput input, boolean allowGroup)
      throws StorageException, IOException, PermissionBackendException, ConfigInvalidException {
    requireNonNull(input.reviewer);
    boolean confirmed = input.confirmed();
    boolean allowByEmail =
        projectCache
            .checkedGet(notes.getProjectName())
            .is(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL);

    ReviewerAddition byAccountId = addByAccountId(input, notes, user);

    ReviewerAddition wholeGroup = null;
    if (!byAccountId.exactMatchFound) {
      wholeGroup = addWholeGroup(input, notes, user, confirmed, allowGroup, allowByEmail);
      if (wholeGroup != null && wholeGroup.exactMatchFound) {
        return wholeGroup;
      }
    }

    if (wholeGroup != null
        && byAccountId.failureType == FailureType.NOT_FOUND
        && wholeGroup.failureType == FailureType.NOT_FOUND) {
      return fail(
          byAccountId.input,
          FailureType.NOT_FOUND,
          byAccountId.result.error + "\n" + wholeGroup.result.error);
    }

    if (byAccountId.failureType != FailureType.NOT_FOUND) {
      return byAccountId;
    }
    if (wholeGroup != null) {
      return wholeGroup;
    }

    return addByEmail(input, notes, user);
  }

  public ReviewerAddition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new ReviewerAddition(
        newAddReviewerInput(user.getUserName().orElse(null), CC, NotifyHandling.NONE),
        revision.getNotes(),
        revision.getUser(),
        ImmutableSet.of(user.getAccountId()),
        null,
        true);
  }

  @Nullable
  private ReviewerAddition addByAccountId(
      AddReviewerInput input, ChangeNotes notes, CurrentUser user)
      throws StorageException, PermissionBackendException, IOException, ConfigInvalidException {
    IdentifiedUser reviewerUser;
    boolean exactMatchFound = false;
    try {
      reviewerUser = accountResolver.resolve(input.reviewer).asUniqueUser();
      if (input.reviewer.equalsIgnoreCase(reviewerUser.getName())
          || input.reviewer.equals(String.valueOf(reviewerUser.getAccountId()))) {
        exactMatchFound = true;
      }
    } catch (UnprocessableEntityException e) {
      // Caller might choose to ignore this NOT_FOUND result if they find another result e.g. by
      // group, but if not, the error message will be useful.
      return fail(input, FailureType.NOT_FOUND, e.getMessage());
    }

    if (isValidReviewer(notes.getChange().getDest(), reviewerUser.getAccount())) {
      return new ReviewerAddition(
          input, notes, user, ImmutableSet.of(reviewerUser.getAccountId()), null, exactMatchFound);
    }
    return fail(
        input,
        FailureType.OTHER,
        MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, input.reviewer));
  }

  @Nullable
  private ReviewerAddition addWholeGroup(
      AddReviewerInput input,
      ChangeNotes notes,
      CurrentUser user,
      boolean confirmed,
      boolean allowGroup,
      boolean allowByEmail)
      throws IOException, PermissionBackendException {
    if (!allowGroup) {
      return null;
    }

    GroupDescription.Basic group;
    try {
      // TODO(dborowitz): This currently doesn't work in the push path because InternalGroupBackend
      // depends on the Provider<CurrentUser> which returns anonymous in that path.
      group = groupResolver.parseInternal(input.reviewer);
    } catch (UnprocessableEntityException e) {
      if (!allowByEmail) {
        return fail(
            input,
            FailureType.NOT_FOUND,
            MessageFormat.format(ChangeMessages.get().reviewerNotFoundUserOrGroup, input.reviewer));
      }
      return null;
    }

    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      return fail(
          input,
          FailureType.OTHER,
          MessageFormat.format(ChangeMessages.get().groupIsNotAllowed, group.getName()));
    }

    Set<Account.Id> reviewers = new HashSet<>();
    Set<Account> members;
    try {
      members = groupMembers.listAccounts(group.getGroupUUID(), notes.getProjectName());
    } catch (NoSuchProjectException e) {
      return fail(input, FailureType.OTHER, e.getMessage());
    }

    // if maxAllowed is set to 0, it is allowed to add any number of
    // reviewers
    int maxAllowed = cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      logger.atFine().log(
          "Adding %d group members is not allowed (maxAllowed = %d)", members.size(), maxAllowed);
      return fail(
          input,
          FailureType.OTHER,
          MessageFormat.format(ChangeMessages.get().groupHasTooManyMembers, group.getName()));
    }

    // if maxWithoutCheck is set to 0, we never ask for confirmation
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation", DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!confirmed && maxWithoutConfirmation > 0 && members.size() > maxWithoutConfirmation) {
      logger.atFine().log(
          "Adding %d group members as reviewer requires confirmation (maxWithoutConfirmation = %d)",
          members.size(), maxWithoutConfirmation);
      return fail(
          input,
          FailureType.OTHER,
          true,
          MessageFormat.format(
              ChangeMessages.get().groupManyMembersConfirmation, group.getName(), members.size()));
    }

    for (Account member : members) {
      if (isValidReviewer(notes.getChange().getDest(), member)) {
        reviewers.add(member.getId());
      }
    }

    return new ReviewerAddition(input, notes, user, reviewers, null, true);
  }

  @Nullable
  private ReviewerAddition addByEmail(AddReviewerInput input, ChangeNotes notes, CurrentUser user)
      throws PermissionBackendException {
    try {
      permissionBackend.user(anonymousProvider.get()).change(notes).check(ChangePermission.READ);
    } catch (AuthException e) {
      return fail(
          input,
          FailureType.OTHER,
          MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, input.reviewer));
    }

    Address adr = Address.tryParse(input.reviewer);
    if (adr == null || !validator.isValid(adr.getEmail())) {
      return fail(
          input,
          FailureType.NOT_FOUND,
          MessageFormat.format(ChangeMessages.get().reviewerInvalid, input.reviewer));
    }
    return new ReviewerAddition(input, notes, user, null, ImmutableList.of(adr), true);
  }

  private boolean isValidReviewer(Branch.NameKey branch, Account member)
      throws PermissionBackendException {
    try {
      // Check ref permission instead of change permission, since change permissions take into
      // account the private bit, whereas adding a user as a reviewer is explicitly allowing them to
      // see private changes.
      permissionBackend.absentUser(member.getId()).ref(branch).check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private ReviewerAddition fail(AddReviewerInput input, FailureType failureType, String error) {
    return fail(input, failureType, false, error);
  }

  private ReviewerAddition fail(
      AddReviewerInput input, FailureType failureType, boolean confirm, String error) {
    ReviewerAddition addition = new ReviewerAddition(input, failureType);
    addition.result.confirm = confirm ? true : null;
    addition.result.error = error;
    return addition;
  }

  public class ReviewerAddition {
    public final AddReviewerResult result;
    @Nullable public final AddReviewersOp op;
    public final ImmutableSet<Account.Id> reviewers;
    public final ImmutableSet<Address> reviewersByEmail;
    @Nullable final IdentifiedUser caller;
    final boolean exactMatchFound;
    private final AddReviewerInput input;
    @Nullable private final FailureType failureType;

    private ReviewerAddition(AddReviewerInput input, FailureType failureType) {
      this.input = input;
      this.failureType = requireNonNull(failureType);
      result = new AddReviewerResult(input.reviewer);
      op = null;
      reviewers = ImmutableSet.of();
      reviewersByEmail = ImmutableSet.of();
      caller = null;
      exactMatchFound = false;
    }

    private ReviewerAddition(
        AddReviewerInput input,
        ChangeNotes notes,
        CurrentUser caller,
        @Nullable Iterable<Account.Id> reviewers,
        @Nullable Iterable<Address> reviewersByEmail,
        boolean exactMatchFound) {
      checkArgument(
          reviewers != null || reviewersByEmail != null,
          "must have either reviewers or reviewersByEmail");

      this.input = input;
      this.failureType = null;
      result = new AddReviewerResult(input.reviewer);
      // Always silently ignore adding the owner as any type of reviewer on their own change. They
      // may still be implicitly added as a reviewer if they vote, but not via the reviewer API.
      this.reviewers = omitOwner(notes, reviewers);
      this.reviewersByEmail =
          reviewersByEmail == null ? ImmutableSet.of() : ImmutableSet.copyOf(reviewersByEmail);
      this.caller = caller.asIdentifiedUser();
      op = addReviewersOpFactory.create(this.reviewers, this.reviewersByEmail, state());
      this.exactMatchFound = exactMatchFound;
    }

    private ImmutableSet<Account.Id> omitOwner(ChangeNotes notes, Iterable<Account.Id> reviewers) {
      return reviewers != null
          ? Streams.stream(reviewers)
              .filter(id -> !id.equals(notes.getChange().getOwner()))
              .collect(toImmutableSet())
          : ImmutableSet.of();
    }

    public void gatherResults(ChangeData cd) throws StorageException, PermissionBackendException {
      checkState(op != null, "addition did not result in an update op");
      checkState(op.getResult() != null, "op did not return a result");

      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      AddReviewersOp.Result opResult = op.getResult();
      if (state() == CC) {
        result.ccs = Lists.newArrayListWithCapacity(opResult.addedCCs().size());
        for (Account.Id accountId : opResult.addedCCs()) {
          result.ccs.add(json.format(new ReviewerInfo(accountId.get()), accountId, cd));
        }
        accountLoaderFactory.create(true).fill(result.ccs);
        for (Address a : opResult.addedCCsByEmail()) {
          result.ccs.add(new AccountInfo(a.getName(), a.getEmail()));
        }
      } else {
        result.reviewers = Lists.newArrayListWithCapacity(opResult.addedReviewers().size());
        for (PatchSetApproval psa : opResult.addedReviewers()) {
          // New reviewers have value 0, don't bother normalizing.
          result.reviewers.add(
              json.format(
                  new ReviewerInfo(psa.getAccountId().get()),
                  psa.getAccountId(),
                  cd,
                  ImmutableList.of(psa)));
        }
        accountLoaderFactory.create(true).fill(result.reviewers);
        for (Address a : opResult.addedReviewersByEmail()) {
          result.reviewers.add(ReviewerInfo.byEmail(a.getName(), a.getEmail()));
        }
      }
    }

    public ReviewerState state() {
      return input.state();
    }

    public boolean isFailure() {
      return failureType != null;
    }

    public boolean isIgnorableFailure() {
      checkState(failureType != null);
      FailureBehavior behavior =
          (input instanceof InternalAddReviewerInput)
              ? ((InternalAddReviewerInput) input).otherFailureBehavior
              : FailureBehavior.FAIL;
      return failureType == FailureType.OTHER && behavior == FailureBehavior.IGNORE;
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }

  public ReviewerAdditionList prepare(
      ChangeNotes notes,
      CurrentUser user,
      Iterable<? extends AddReviewerInput> inputs,
      boolean allowGroup)
      throws StorageException, IOException, PermissionBackendException, ConfigInvalidException {
    // Process CC ops before reviewer ops, so a user that appears in both lists ends up as a
    // reviewer; the last call to ChangeUpdate#putReviewer wins. This can happen if the caller
    // specifies the same string twice, or less obviously if they specify multiple groups with
    // overlapping members.
    // TODO(dborowitz): Consider changing interface to allow excluding reviewers that were
    // previously processed, to proactively prevent overlap so we don't have to rely on this subtle
    // behavior.
    ImmutableList<AddReviewerInput> sorted =
        Streams.stream(inputs)
            .sorted(
                comparing(
                    AddReviewerInput::state,
                    Ordering.explicit(ReviewerState.CC, ReviewerState.REVIEWER)))
            .collect(toImmutableList());
    List<ReviewerAddition> additions = new ArrayList<>();
    for (AddReviewerInput input : sorted) {
      ReviewerAddition addition = prepare(notes, user, input, allowGroup);
      if (addition.op != null) {
        // Assume any callers preparing a list of batch insertions are handling their own email.
        addition.op.suppressEmail();
      }
      additions.add(addition);
    }
    return new ReviewerAdditionList(additions);
  }

  // TODO(dborowitz): This class works, but ultimately feels wrong. It seems like an op but isn't
  // really an op, it's a collection of ops, and it's only called from the body of other ops. We
  // could make this class an op, but we would still have AddReviewersOp. Better would probably be
  // to design a single op that supports combining multiple AddReviewerInputs together. That would
  // probably also subsume the Addition class itself, which would be a good thing.
  public static class ReviewerAdditionList {
    private final ImmutableList<ReviewerAddition> additions;

    private ReviewerAdditionList(List<ReviewerAddition> additions) {
      this.additions = ImmutableList.copyOf(additions);
    }

    public ImmutableList<ReviewerAddition> getFailures() {
      return additions.stream()
          .filter(a -> a.isFailure() && !a.isIgnorableFailure())
          .collect(toImmutableList());
    }

    // We never call updateRepo on the addition ops, which is only ok because it's a no-op.

    public void updateChange(ChangeContext ctx, PatchSet patchSet)
        throws StorageException, RestApiException, IOException {
      for (ReviewerAddition addition : additions()) {
        addition.op.setPatchSet(patchSet);
        addition.op.updateChange(ctx);
      }
    }

    public void postUpdate(Context ctx) throws Exception {
      for (ReviewerAddition addition : additions()) {
        if (addition.op != null) {
          addition.op.postUpdate(ctx);
        }
      }
    }

    public <T> ImmutableSet<T> flattenResults(
        Function<AddReviewersOp.Result, ? extends Collection<T>> func) {
      additions()
          .forEach(
              a ->
                  checkArgument(
                      a.op != null && a.op.getResult() != null, "missing result on %s", a));
      return additions().stream()
          .map(a -> a.op.getResult())
          .map(func)
          .flatMap(Collection::stream)
          .collect(toImmutableSet());
    }

    private ImmutableList<ReviewerAddition> additions() {
      return additions.stream()
          .filter(
              a -> {
                if (a.isFailure()) {
                  if (a.isIgnorableFailure()) {
                    return false;
                  }
                  // Shouldn't happen, caller should have checked that there were no errors.
                  throw new IllegalStateException("error in addition: " + a.result.error);
                }
                return true;
              })
          .collect(toImmutableList());
    }
  }
}
