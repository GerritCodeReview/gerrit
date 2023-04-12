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
import static com.google.gerrit.extensions.client.ReviewerState.REMOVED;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
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
import com.google.gerrit.server.update.PostUpdateContext;
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
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;

public class ReviewerModifier {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  /**
   * Controls which failures should be ignored.
   *
   * <p>If a failure is ignored the operation succeeds, but the reviewer is not added. If not
   * ignored a failure means that the operation fails.
   */
  public enum FailureBehavior {
    // All failures cause the operation to fail.
    FAIL,

    // Only not found failures cause the operation to fail, all other failures are ignored.
    IGNORE_EXCEPT_NOT_FOUND,

    // All failures are ignored.
    IGNORE_ALL;
  }

  private enum FailureType {
    NOT_FOUND,
    OTHER;
  }

  // TODO(dborowitz): Subclassing is not the right way to do this. We should instead use an internal
  // type in the public interfaces of ReviewerModifier, rather than passing around the REST API type
  // internally.
  public static class InternalReviewerInput extends ReviewerInput {
    /**
     * Behavior when identifying reviewers fails for any reason <em>besides</em> the input not
     * resolving to an account/group/email.
     */
    public FailureBehavior otherFailureBehavior = FailureBehavior.FAIL;

    /** Whether the visibility check for the reviewer account should be skipped. */
    public boolean skipVisibilityCheck = false;
  }

  public static InternalReviewerInput newReviewerInput(
      String reviewer, ReviewerState state, NotifyHandling notify) {
    InternalReviewerInput in = new InternalReviewerInput();
    in.reviewer = reviewer;
    in.state = state;
    in.notify = notify;
    return in;
  }

  public static Optional<InternalReviewerInput> newReviewerInputFromCommitIdentity(
      Change change,
      ObjectId commitId,
      @Nullable Account.Id accountId,
      NotifyHandling notify,
      Account.Id mostRecentUploader) {
    if (accountId == null || accountId.equals(mostRecentUploader)) {
      // If git ident couldn't be resolved to a user, or if it's not forged, do nothing.
      return Optional.empty();
    }

    logger.atFine().log(
        "Adding account %d from author/committer identity of commit %s as cc to change %d",
        accountId.get(), commitId.name(), change.getChangeId());

    InternalReviewerInput in = new InternalReviewerInput();
    in.reviewer = accountId.toString();
    in.state = CC;
    in.notify = notify;
    in.otherFailureBehavior = FailureBehavior.IGNORE_ALL;
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
  private final DeleteReviewerOp.Factory deleteReviewerOpFactory;
  private final DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory;

  @Inject
  ReviewerModifier(
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
      OutgoingEmailValidator validator,
      DeleteReviewerOp.Factory deleteReviewerOpFactory,
      DeleteReviewerByEmailOp.Factory deleteReviewerByEmailOpFactory) {
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
    this.deleteReviewerOpFactory = deleteReviewerOpFactory;
    this.deleteReviewerByEmailOpFactory = deleteReviewerByEmailOpFactory;
  }

  /**
   * Prepare application of a single {@link ReviewerInput}.
   *
   * @param notes change notes.
   * @param user user performing the reviewer addition.
   * @param input input describing user or group to add as a reviewer.
   * @param allowGroup whether to allow
   * @return handle describing the addition operation. If the {@code op} field is present, this
   *     operation may be added to a {@code BatchUpdate}. Otherwise, the {@code error} field
   *     contains information about an error that occurred
   */
  public ReviewerModification prepare(
      ChangeNotes notes, CurrentUser user, ReviewerInput input, boolean allowGroup)
      throws IOException, PermissionBackendException, ConfigInvalidException {
    try (TraceContext.TraceTimer ignored =
        TraceContext.newTimer(getClass().getSimpleName() + "#prepare", Metadata.empty())) {
      requireNonNull(input.reviewer);
      boolean confirmed = input.confirmed();
      boolean allowByEmail =
          projectCache
              .get(notes.getProjectName())
              .orElseThrow(illegalState(notes.getProjectName()))
              .is(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL);

      ReviewerModification byAccountId = byAccountId(input, notes, user);

      ReviewerModification wholeGroup = null;
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
  }

  public ReviewerModification ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new ReviewerModification(
        newReviewerInput(user.getUserName().orElse(null), CC, NotifyHandling.NONE),
        revision.getNotes(),
        revision.getUser(),
        ImmutableSet.of(user.asIdentifiedUser().getAccount()),
        null,
        true,
        false);
  }

  @Nullable
  private ReviewerModification byAccountId(ReviewerInput input, ChangeNotes notes, CurrentUser user)
      throws PermissionBackendException, IOException, ConfigInvalidException {
    IdentifiedUser reviewerUser;
    boolean exactMatchFound = false;
    try {
      if (ReviewerState.REMOVED.equals(input.state)
          || (input instanceof InternalReviewerInput
              && ((InternalReviewerInput) input).skipVisibilityCheck)) {
        reviewerUser =
            accountResolver.resolveIncludeInactiveIgnoreVisibility(input.reviewer).asUniqueUser();
      } else {
        reviewerUser = accountResolver.resolveIncludeInactive(input.reviewer).asUniqueUser();
      }
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
      return new ReviewerModification(
          input,
          notes,
          user,
          ImmutableSet.of(reviewerUser.getAccount()),
          null,
          exactMatchFound,
          false);
    }
    return fail(
        input,
        FailureType.OTHER,
        MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, input.reviewer));
  }

  @Nullable
  private ReviewerModification addWholeGroup(
      ReviewerInput input,
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

    if (input.state().equals(REMOVED)) {
      return fail(
          input,
          FailureType.OTHER,
          MessageFormat.format(ChangeMessages.get().groupRemovalIsNotAllowed, group.getName()));
    }

    Set<Account> reviewers = new HashSet<>();
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
        reviewers.add(member);
      }
    }

    return new ReviewerModification(input, notes, user, reviewers, null, true, true);
  }

  @Nullable
  private ReviewerModification addByEmail(ReviewerInput input, ChangeNotes notes, CurrentUser user)
      throws PermissionBackendException {
    if (!permissionBackend
        .user(anonymousProvider.get())
        .change(notes)
        .test(ChangePermission.READ)) {
      return fail(
          input,
          FailureType.OTHER,
          MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, input.reviewer));
    }

    Address adr = Address.tryParse(input.reviewer);
    if (adr == null || !validator.isValid(adr.email())) {
      return fail(
          input,
          FailureType.NOT_FOUND,
          MessageFormat.format(ChangeMessages.get().reviewerInvalid, input.reviewer));
    }
    return new ReviewerModification(input, notes, user, null, ImmutableList.of(adr), true, false);
  }

  private boolean isValidReviewer(BranchNameKey branch, Account member)
      throws PermissionBackendException {
    // Check ref permission instead of change permission, since change permissions take into
    // account the private bit, whereas adding a user as a reviewer is explicitly allowing them to
    // see private changes.
    return permissionBackend.absentUser(member.id()).ref(branch).test(RefPermission.READ);
  }

  private ReviewerModification fail(ReviewerInput input, FailureType failureType, String error) {
    return fail(input, failureType, false, error);
  }

  private ReviewerModification fail(
      ReviewerInput input, FailureType failureType, boolean confirm, String error) {
    ReviewerModification addition = new ReviewerModification(input, failureType);
    addition.result.confirm = confirm ? true : null;
    addition.result.error = error;
    return addition;
  }

  public class ReviewerModification {
    public final ReviewerResult result;
    @Nullable public final ReviewerOp op;
    public final ImmutableSet<Account> reviewers;
    public final ImmutableSet<Address> reviewersByEmail;
    @Nullable final IdentifiedUser caller;
    final boolean exactMatchFound;
    private final ReviewerInput input;
    @Nullable private final FailureType failureType;

    private ReviewerModification(ReviewerInput input, FailureType failureType) {
      this.input = input;
      this.failureType = requireNonNull(failureType);
      result = new ReviewerResult(input.reviewer);
      op = null;
      reviewers = ImmutableSet.of();
      reviewersByEmail = ImmutableSet.of();
      caller = null;
      exactMatchFound = false;
    }

    private ReviewerModification(
        ReviewerInput input,
        ChangeNotes notes,
        CurrentUser caller,
        @Nullable Iterable<Account> reviewers,
        @Nullable Iterable<Address> reviewersByEmail,
        boolean exactMatchFound,
        boolean forGroup) {
      checkArgument(
          reviewers != null || reviewersByEmail != null,
          "must have either reviewers or reviewersByEmail");

      this.input = input;
      this.failureType = null;
      result = new ReviewerResult(input.reviewer);
      if (!state().equals(REMOVED)) {
        // Always silently ignore adding the owner as any type of reviewer on their own change. They
        // may still be implicitly added as a reviewer if they vote, but not via the reviewer API.
        this.reviewers = reviewersAsList(notes, reviewers, /* omitChangeOwner= */ true);
      } else {
        this.reviewers = reviewersAsList(notes, reviewers, /* omitChangeOwner= */ false);
      }
      this.reviewersByEmail =
          reviewersByEmail == null ? ImmutableSet.of() : ImmutableSet.copyOf(reviewersByEmail);
      this.caller = caller.asIdentifiedUser();
      if (state().equals(REMOVED)) {
        // only one is set.
        checkState(
            (this.reviewers.size() == 1 && this.reviewersByEmail.isEmpty())
                || (this.reviewers.isEmpty() && this.reviewersByEmail.size() == 1));
        if (this.reviewers.size() >= 1) {
          checkState(this.reviewers.size() == 1);
          DeleteReviewerInput deleteReviewerInput = new DeleteReviewerInput();
          deleteReviewerInput.notify = input.notify;
          deleteReviewerInput.notifyDetails = input.notifyDetails;
          op =
              deleteReviewerOpFactory.create(
                  Iterables.getOnlyElement(this.reviewers.asList()), deleteReviewerInput);
        } else {
          checkState(this.reviewersByEmail.size() == 1);
          op =
              deleteReviewerByEmailOpFactory.create(
                  Iterables.getOnlyElement(this.reviewersByEmail.asList()));
        }
      } else {
        op =
            addReviewersOpFactory.create(
                this.reviewers.stream().map(Account::id).collect(toImmutableSet()),
                this.reviewersByEmail,
                state(),
                forGroup);
      }
      this.exactMatchFound = exactMatchFound;
    }

    private ImmutableSet<Account> reviewersAsList(
        ChangeNotes notes, @Nullable Iterable<Account> reviewers, boolean omitChangeOwner) {
      if (reviewers == null) {
        return ImmutableSet.of();
      }

      Stream<Account> reviewerStream = Streams.stream(reviewers);
      if (omitChangeOwner) {
        reviewerStream =
            reviewerStream.filter(account -> !account.id().equals(notes.getChange().getOwner()));
      }
      return reviewerStream.collect(toImmutableSet());
    }

    public void gatherResults(ChangeData cd) throws PermissionBackendException {
      checkState(op != null, "addition did not result in an update op");
      checkState(op.getResult() != null, "op did not return a result");

      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      ReviewerOp.Result opResult = op.getResult();
      switch (state()) {
        case CC:
          result.ccs = Lists.newArrayListWithCapacity(opResult.addedCCs().size());
          for (Account.Id accountId : opResult.addedCCs()) {
            result.ccs.add(json.format(new ReviewerInfo(accountId.get()), accountId, cd));
          }
          accountLoaderFactory.create(true).fill(result.ccs);
          for (Address a : opResult.addedCCsByEmail()) {
            result.ccs.add(new AccountInfo(a.name(), a.email()));
          }
          break;
        case REVIEWER:
          result.reviewers = Lists.newArrayListWithCapacity(opResult.addedReviewers().size());
          for (PatchSetApproval psa : opResult.addedReviewers()) {
            // New reviewers have value 0, don't bother normalizing.
            result.reviewers.add(
                json.format(
                    new ReviewerInfo(psa.accountId().get()),
                    psa.accountId(),
                    cd,
                    ImmutableList.of(psa)));
          }
          accountLoaderFactory.create(true).fill(result.reviewers);
          for (Address a : opResult.addedReviewersByEmail()) {
            result.reviewers.add(ReviewerInfo.byEmail(a.name(), a.email()));
          }
          break;
        case REMOVED:
          if (opResult.deletedReviewer().isPresent()) {
            result.removed =
                json.format(
                    new ReviewerInfo(opResult.deletedReviewer().get().get()),
                    opResult.deletedReviewer().get(),
                    cd);
            accountLoaderFactory.create(true).fill(ImmutableList.of(result.removed));
          } else if (opResult.deletedReviewerByEmail().isPresent()) {
            result.removed =
                new AccountInfo(
                    opResult.deletedReviewerByEmail().get().name(),
                    opResult.deletedReviewerByEmail().get().email());
          }
          break;
        default:
          throw new IllegalStateException(
              String.format("Illegal ReviewerState argument is %s", state().name()));
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
          (input instanceof InternalReviewerInput)
              ? ((InternalReviewerInput) input).otherFailureBehavior
              : FailureBehavior.FAIL;
      return behavior == FailureBehavior.IGNORE_ALL
          || (failureType == FailureType.OTHER
              && behavior == FailureBehavior.IGNORE_EXCEPT_NOT_FOUND);
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }

  public ReviewerModificationList prepare(
      ChangeNotes notes,
      CurrentUser user,
      Iterable<? extends ReviewerInput> inputs,
      boolean allowGroup)
      throws IOException, PermissionBackendException, ConfigInvalidException {
    // Process CC ops before reviewer ops, so a user that appears in both lists ends up as a
    // reviewer; the last call to ChangeUpdate#putReviewer wins. This can happen if the caller
    // specifies the same string twice, or less obviously if they specify multiple groups with
    // overlapping members.
    // TODO(dborowitz): Consider changing interface to allow excluding reviewers that were
    // previously processed, to proactively prevent overlap so we don't have to rely on this subtle
    // behavior.
    ImmutableList<ReviewerInput> sorted =
        Streams.stream(inputs)
            .sorted(
                comparing(
                    ReviewerInput::state,
                    Ordering.explicit(ReviewerState.CC, ReviewerState.REVIEWER)))
            .collect(toImmutableList());
    List<ReviewerModification> additions = new ArrayList<>();
    for (ReviewerInput input : sorted) {
      ReviewerModification addition = prepare(notes, user, input, allowGroup);
      if (addition.op != null) {
        // Assume any callers preparing a list of batch insertions are handling their own email.
        addition.op.suppressEmail();
      }
      additions.add(addition);
    }
    return new ReviewerModificationList(additions);
  }

  // TODO(dborowitz): This class works, but ultimately feels wrong. It seems like an op but isn't
  // really an op, it's a collection of ops, and it's only called from the body of other ops. We
  // could make this class an op, but we would still have AddReviewersOp. Better would probably be
  // to design a single op that supports combining multiple ReviewerInputs together. That would
  // probably also subsume the Addition class itself, which would be a good thing.
  public static class ReviewerModificationList {
    private final ImmutableList<ReviewerModification> modifications;

    private ReviewerModificationList(List<ReviewerModification> modifications) {
      this.modifications = ImmutableList.copyOf(modifications);
    }

    public ImmutableList<ReviewerModification> getFailures() {
      return modifications.stream()
          .filter(a -> a.isFailure() && !a.isIgnorableFailure())
          .collect(toImmutableList());
    }

    // We never call updateRepo on the addition ops, which is only ok because it's a no-op.

    public void updateChange(ChangeContext ctx, PatchSet patchSet)
        throws RestApiException, IOException, PermissionBackendException {
      for (ReviewerModification addition : modifications()) {
        addition.op.setPatchSet(patchSet);
        addition.op.updateChange(ctx);
      }
    }

    public void postUpdate(PostUpdateContext ctx) throws Exception {
      for (ReviewerModification addition : modifications()) {
        if (addition.op != null) {
          addition.op.postUpdate(ctx);
        }
      }
    }

    public <T> ImmutableSet<T> flattenResults(
        Function<ReviewerOp.Result, ? extends Collection<T>> func) {
      modifications()
          .forEach(
              a ->
                  checkArgument(
                      a.op != null && a.op.getResult() != null, "missing result on %s", a));
      return modifications().stream()
          .map(a -> a.op.getResult())
          .map(func)
          .flatMap(Collection::stream)
          .collect(toImmutableSet());
    }

    private ImmutableList<ReviewerModification> modifications() {
      return modifications.stream()
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
