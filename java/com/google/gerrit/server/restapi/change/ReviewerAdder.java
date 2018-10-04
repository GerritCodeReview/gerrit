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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class ReviewerAdder {
  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  private final AccountResolver accountResolver;
  private final PermissionBackend permissionBackend;
  private final GroupResolver groupResolver;
  private final GroupMembers groupMembers;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final Config cfg;
  private final ReviewerJson json;
  private final NotesMigration migration;
  private final NotifyUtil notifyUtil;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousProvider;
  private final PostReviewersOp.Factory postReviewersOpFactory;
  private final OutgoingEmailValidator validator;

  @Inject
  ReviewerAdder(
      AccountResolver accountResolver,
      PermissionBackend permissionBackend,
      GroupResolver groupResolver,
      GroupMembers groupMembers,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      NotesMigration migration,
      NotifyUtil notifyUtil,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider,
      PostReviewersOp.Factory postReviewersOpFactory,
      OutgoingEmailValidator validator) {
    this.accountResolver = accountResolver;
    this.permissionBackend = permissionBackend;
    this.groupResolver = groupResolver;
    this.groupMembers = groupMembers;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.cfg = cfg;
    this.json = json;
    this.migration = migration;
    this.notifyUtil = notifyUtil;
    this.projectCache = projectCache;
    this.anonymousProvider = anonymousProvider;
    this.postReviewersOpFactory = postReviewersOpFactory;
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
   * @throws OrmException
   * @throws IOException
   * @throws PermissionBackendException
   * @throws ConfigInvalidException
   */
  public ReviewerAddition prepare(
      ChangeNotes notes, CurrentUser user, AddReviewerInput input, boolean allowGroup)
      throws OrmException, IOException, PermissionBackendException, ConfigInvalidException {
    Branch.NameKey dest = notes.getChange().getDest();
    String reviewer = checkNotNull(input.reviewer);
    ReviewerState state = input.state();
    NotifyHandling notify = input.notify;
    ListMultimap<RecipientType, Account.Id> accountsToNotify;
    try {
      accountsToNotify = notifyUtil.resolveAccounts(input.notifyDetails);
    } catch (BadRequestException e) {
      return fail(reviewer, e.getMessage());
    }
    boolean confirmed = input.confirmed();
    boolean allowByEmail =
        projectCache
            .checkedGet(dest.getParentKey())
            .is(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL);

    ReviewerAddition byAccountId =
        addByAccountId(
            reviewer, dest, user, state, notify, accountsToNotify, allowGroup, allowByEmail);

    ReviewerAddition wholeGroup = null;
    if (byAccountId == null || !byAccountId.exactMatchFound) {
      wholeGroup =
          addWholeGroup(
              reviewer,
              dest,
              user,
              state,
              notify,
              accountsToNotify,
              confirmed,
              allowGroup,
              allowByEmail);
      if (wholeGroup != null && wholeGroup.exactMatchFound) {
        return wholeGroup;
      }
    }

    if (byAccountId != null) {
      return byAccountId;
    }
    if (wholeGroup != null) {
      return wholeGroup;
    }

    return addByEmail(reviewer, notes, user, state, notify, accountsToNotify);
  }

  ReviewerAddition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new ReviewerAddition(
        user.getUserName().orElse(null),
        revision.getUser(),
        ImmutableSet.of(user.getAccountId()),
        null,
        CC,
        NotifyHandling.NONE,
        ImmutableListMultimap.of(),
        true);
  }

  @Nullable
  private ReviewerAddition addByAccountId(
      String reviewer,
      Branch.NameKey dest,
      CurrentUser user,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify,
      boolean allowGroup,
      boolean allowByEmail)
      throws OrmException, PermissionBackendException, IOException, ConfigInvalidException {
    IdentifiedUser reviewerUser;
    boolean exactMatchFound = false;
    try {
      reviewerUser = accountResolver.parse(reviewer);
      if (reviewer.equalsIgnoreCase(reviewerUser.getName())
          || reviewer.equals(String.valueOf(reviewerUser.getAccountId()))) {
        exactMatchFound = true;
      }
    } catch (UnprocessableEntityException | AuthException e) {
      // AuthException won't occur since the user is authenticated at this point.
      if (!allowGroup && !allowByEmail) {
        // Only return failure if we aren't going to try other interpretations.
        return fail(
            reviewer, MessageFormat.format(ChangeMessages.get().reviewerNotFoundUser, reviewer));
      }
      return null;
    }

    if (isValidReviewer(dest, reviewerUser.getAccount())) {
      return new ReviewerAddition(
          reviewer,
          user,
          ImmutableSet.of(reviewerUser.getAccountId()),
          null,
          state,
          notify,
          accountsToNotify,
          exactMatchFound);
    }
    if (!reviewerUser.getAccount().isActive()) {
      if (allowByEmail && state == CC) {
        return null;
      }
      return fail(reviewer, MessageFormat.format(ChangeMessages.get().reviewerInactive, reviewer));
    }
    return fail(
        reviewer, MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, reviewer));
  }

  @Nullable
  private ReviewerAddition addWholeGroup(
      String reviewer,
      Branch.NameKey dest,
      CurrentUser user,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify,
      boolean confirmed,
      boolean allowGroup,
      boolean allowByEmail)
      throws IOException, PermissionBackendException {
    if (!allowGroup) {
      return null;
    }

    GroupDescription.Basic group;
    try {
      group = groupResolver.parseInternal(reviewer);
    } catch (UnprocessableEntityException e) {
      if (!allowByEmail) {
        return fail(
            reviewer,
            MessageFormat.format(ChangeMessages.get().reviewerNotFoundUserOrGroup, reviewer));
      }
      return null;
    }

    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      return fail(
          reviewer, MessageFormat.format(ChangeMessages.get().groupIsNotAllowed, group.getName()));
    }

    Set<Account.Id> reviewers = new HashSet<>();
    Set<Account> members;
    try {
      members = groupMembers.listAccounts(group.getGroupUUID(), dest.getParentKey());
    } catch (NoSuchProjectException e) {
      return fail(reviewer, e.getMessage());
    }

    // if maxAllowed is set to 0, it is allowed to add any number of
    // reviewers
    int maxAllowed = cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      return fail(
          reviewer,
          MessageFormat.format(ChangeMessages.get().groupHasTooManyMembers, group.getName()));
    }

    // if maxWithoutCheck is set to 0, we never ask for confirmation
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation", DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!confirmed && maxWithoutConfirmation > 0 && members.size() > maxWithoutConfirmation) {
      return fail(
          reviewer,
          true,
          MessageFormat.format(
              ChangeMessages.get().groupManyMembersConfirmation, group.getName(), members.size()));
    }

    for (Account member : members) {
      if (isValidReviewer(dest, member)) {
        reviewers.add(member.getId());
      }
    }

    return new ReviewerAddition(
        reviewer, user, reviewers, null, state, notify, accountsToNotify, true);
  }

  @Nullable
  private ReviewerAddition addByEmail(
      String reviewer,
      ChangeNotes notes,
      CurrentUser user,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws PermissionBackendException {
    try {
      permissionBackend
          .user(anonymousProvider.get())
          .database(dbProvider)
          .change(notes)
          .check(ChangePermission.READ);
    } catch (AuthException e) {
      return fail(
          reviewer, MessageFormat.format(ChangeMessages.get().reviewerCantSeeChange, reviewer));
    }

    if (!migration.readChanges()) {
      // addByEmail depends on NoteDb.
      return fail(
          reviewer, MessageFormat.format(ChangeMessages.get().reviewerNotFoundUser, reviewer));
    }
    Address adr = Address.tryParse(reviewer);
    if (adr == null || !validator.isValid(adr.getEmail())) {
      return fail(reviewer, MessageFormat.format(ChangeMessages.get().reviewerInvalid, reviewer));
    }
    return new ReviewerAddition(
        reviewer, user, null, ImmutableList.of(adr), state, notify, accountsToNotify, true);
  }

  private boolean isValidReviewer(Branch.NameKey branch, Account member)
      throws PermissionBackendException {
    if (!member.isActive()) {
      return false;
    }

    try {
      // Check ref permission instead of change permission, since change permissions take into
      // account the private bit, whereas adding a user as a reviewer is explicitly allowing them to
      // see private changes.
      permissionBackend
          .absentUser(member.getId())
          .database(dbProvider)
          .ref(branch)
          .check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private ReviewerAddition fail(String reviewer, String error) {
    return fail(reviewer, false, error);
  }

  private ReviewerAddition fail(String reviewer, boolean confirm, String error) {
    ReviewerAddition addition = new ReviewerAddition(reviewer);
    addition.result.confirm = confirm ? true : null;
    addition.result.error = error;
    return addition;
  }

  public class ReviewerAddition {
    public final AddReviewerResult result;
    @Nullable public final PostReviewersOp op;
    final Set<Id> reviewers;
    final Collection<Address> reviewersByEmail;
    final ReviewerState state;
    @Nullable final IdentifiedUser caller;
    final boolean exactMatchFound;

    ReviewerAddition(String reviewer) {
      result = new AddReviewerResult(reviewer);
      op = null;
      reviewers = ImmutableSet.of();
      reviewersByEmail = ImmutableSet.of();
      state = REVIEWER;
      caller = null;
      exactMatchFound = false;
    }

    ReviewerAddition(
        String reviewer,
        CurrentUser caller,
        @Nullable Set<Account.Id> reviewers,
        @Nullable Collection<Address> reviewersByEmail,
        ReviewerState state,
        @Nullable NotifyHandling notify,
        ListMultimap<RecipientType, Id> accountsToNotify,
        boolean exactMatchFound) {
      checkArgument(
          reviewers != null || reviewersByEmail != null,
          "must have either reviewers or reviewersByEmail");

      result = new AddReviewerResult(reviewer);
      this.reviewers = reviewers == null ? ImmutableSet.of() : reviewers;
      this.reviewersByEmail = reviewersByEmail == null ? ImmutableList.of() : reviewersByEmail;
      this.state = state;
      this.caller = caller.asIdentifiedUser();
      op =
          postReviewersOpFactory.create(
              this.reviewers, this.reviewersByEmail, state, notify, accountsToNotify);
      this.exactMatchFound = exactMatchFound;
    }

    void gatherResults(ChangeData cd) throws OrmException, PermissionBackendException {
      checkState(op != null, "addition did not result in an update op");
      checkState(op.getResult() != null, "op did not return a result");

      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      PostReviewersOp.Result opResult = op.getResult();
      if (migration.readChanges() && state == CC) {
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
        // TODO(dborowitz): Include addedReviewersByEmail.
        accountLoaderFactory.create(true).fill(result.reviewers);
        for (Address a : reviewersByEmail) {
          result.reviewers.add(ReviewerInfo.byEmail(a.getName(), a.getEmail()));
        }
      }
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }
}
