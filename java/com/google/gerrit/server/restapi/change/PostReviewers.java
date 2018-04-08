// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.ChangeMessages;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.Address;
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
import com.google.gerrit.server.restapi.account.AccountsCollection;
import com.google.gerrit.server.restapi.group.GroupsCollection;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class PostReviewers
    extends RetryingRestModifyView<ChangeResource, AddReviewerInput, AddReviewerResult> {

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  private final AccountsCollection accounts;
  private final PermissionBackend permissionBackend;

  private final GroupsCollection groupsCollection;
  private final GroupMembers groupMembers;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeData.Factory changeDataFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final ReviewerJson json;
  private final NotesMigration migration;
  private final NotifyUtil notifyUtil;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousProvider;
  private final PostReviewersOp.Factory postReviewersOpFactory;
  private final OutgoingEmailValidator validator;

  @Inject
  PostReviewers(
      AccountsCollection accounts,
      PermissionBackend permissionBackend,
      GroupsCollection groupsCollection,
      GroupMembers groupMembers,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      RetryHelper retryHelper,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      NotesMigration migration,
      NotifyUtil notifyUtil,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider,
      PostReviewersOp.Factory postReviewersOpFactory,
      OutgoingEmailValidator validator) {
    super(retryHelper);
    this.accounts = accounts;
    this.permissionBackend = permissionBackend;
    this.groupsCollection = groupsCollection;
    this.groupMembers = groupMembers;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.changeDataFactory = changeDataFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.json = json;
    this.migration = migration;
    this.notifyUtil = notifyUtil;
    this.projectCache = projectCache;
    this.anonymousProvider = anonymousProvider;
    this.postReviewersOpFactory = postReviewersOpFactory;
    this.validator = validator;
  }

  @Override
  protected AddReviewerResult applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AddReviewerInput input)
      throws IOException, OrmException, RestApiException, UpdateException,
          PermissionBackendException, ConfigInvalidException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    Addition addition = prepareApplication(rsrc, input, true);
    if (addition.op == null) {
      return addition.result;
    }
    try (BatchUpdate bu =
        updateFactory.create(
            dbProvider.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Change.Id id = rsrc.getChange().getId();
      bu.addOp(id, addition.op);
      bu.execute();
      addition.gatherResults();
    }
    return addition.result;
  }

  public Addition prepareApplication(
      ChangeResource rsrc, AddReviewerInput input, boolean allowGroup)
      throws OrmException, IOException, PermissionBackendException, ConfigInvalidException {
    String reviewer = input.reviewer;
    ReviewerState state = input.state();
    NotifyHandling notify = input.notify;
    ListMultimap<RecipientType, Account.Id> accountsToNotify = null;
    try {
      accountsToNotify = notifyUtil.resolveAccounts(input.notifyDetails);
    } catch (BadRequestException e) {
      return fail(reviewer, e.getMessage());
    }
    boolean confirmed = input.confirmed();
    boolean allowByEmail =
        projectCache
            .checkedGet(rsrc.getProject())
            .is(BooleanProjectConfig.ENABLE_REVIEWER_BY_EMAIL);

    Addition byAccountId =
        addByAccountId(reviewer, rsrc, state, notify, accountsToNotify, allowGroup, allowByEmail);

    Addition wholeGroup = null;
    if (byAccountId == null || !byAccountId.exactMatchFound) {
      wholeGroup =
          addWholeGroup(
              reviewer, rsrc, state, notify, accountsToNotify, confirmed, allowGroup, allowByEmail);
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

    return addByEmail(reviewer, rsrc, state, notify, accountsToNotify);
  }

  Addition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new Addition(
        user.getUserName().orElse(null),
        revision.getChangeResource(),
        ImmutableSet.of(user.getAccountId()),
        null,
        CC,
        NotifyHandling.NONE,
        ImmutableListMultimap.of(),
        true);
  }

  @Nullable
  private Addition addByAccountId(
      String reviewer,
      ChangeResource rsrc,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify,
      boolean allowGroup,
      boolean allowByEmail)
      throws OrmException, PermissionBackendException, IOException, ConfigInvalidException {
    IdentifiedUser reviewerUser = null;
    boolean exactMatchFound = false;
    try {
      reviewerUser = accounts.parse(reviewer);
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

    PermissionBackend.ForRef perm =
        permissionBackend.absentUser(reviewerUser.getAccountId()).ref(rsrc.getChange().getDest());
    if (isValidReviewer(reviewerUser.getAccount(), perm)) {
      return new Addition(
          reviewer,
          rsrc,
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
  private Addition addWholeGroup(
      String reviewer,
      ChangeResource rsrc,
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

    GroupDescription.Basic group = null;
    try {
      group = groupsCollection.parseInternal(reviewer);
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
      members = groupMembers.listAccounts(group.getGroupUUID(), rsrc.getProject());
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

    PermissionBackend.ForRef perm =
        permissionBackend.user(rsrc.getUser()).ref(rsrc.getChange().getDest());
    for (Account member : members) {
      if (isValidReviewer(member, perm)) {
        reviewers.add(member.getId());
      }
    }

    return new Addition(reviewer, rsrc, reviewers, null, state, notify, accountsToNotify, true);
  }

  @Nullable
  private Addition addByEmail(
      String reviewer,
      ChangeResource rsrc,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws PermissionBackendException {
    if (!permissionBackend
        .user(anonymousProvider.get())
        .change(rsrc.getNotes())
        .database(dbProvider)
        .test(ChangePermission.READ)) {
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
    return new Addition(
        reviewer, rsrc, null, ImmutableList.of(adr), state, notify, accountsToNotify, true);
  }

  private boolean isValidReviewer(Account member, PermissionBackend.ForRef perm)
      throws PermissionBackendException {
    if (member.isActive()) {
      IdentifiedUser user = identifiedUserFactory.create(member.getId());
      // Does not account for draft status as a user might want to let a
      // reviewer see a draft.
      try {
        perm.user(user).check(RefPermission.READ);
        return true;
      } catch (AuthException e) {
        return false;
      }
    }
    return false;
  }

  private Addition fail(String reviewer, String error) {
    return fail(reviewer, false, error);
  }

  private Addition fail(String reviewer, boolean confirm, String error) {
    Addition addition = new Addition(reviewer);
    addition.result.confirm = confirm ? true : null;
    addition.result.error = error;
    return addition;
  }

  public class Addition {
    final AddReviewerResult result;
    final PostReviewersOp op;
    final Set<Account.Id> reviewers;
    final Collection<Address> reviewersByEmail;
    final ReviewerState state;
    final ChangeNotes notes;
    final IdentifiedUser caller;
    final boolean exactMatchFound;

    Addition(String reviewer) {
      result = new AddReviewerResult(reviewer);
      op = null;
      reviewers = ImmutableSet.of();
      reviewersByEmail = ImmutableSet.of();
      state = REVIEWER;
      notes = null;
      caller = null;
      exactMatchFound = false;
    }

    protected Addition(
        String reviewer,
        ChangeResource rsrc,
        @Nullable Set<Account.Id> reviewers,
        @Nullable Collection<Address> reviewersByEmail,
        ReviewerState state,
        @Nullable NotifyHandling notify,
        ListMultimap<RecipientType, Account.Id> accountsToNotify,
        boolean exactMatchFound) {
      checkArgument(
          reviewers != null || reviewersByEmail != null,
          "must have either reviewers or reviewersByEmail");

      result = new AddReviewerResult(reviewer);
      this.reviewers = reviewers == null ? ImmutableSet.of() : reviewers;
      this.reviewersByEmail = reviewersByEmail == null ? ImmutableList.of() : reviewersByEmail;
      this.state = state;
      notes = rsrc.getNotes();
      caller = rsrc.getUser().asIdentifiedUser();
      op =
          postReviewersOpFactory.create(
              rsrc, this.reviewers, this.reviewersByEmail, state, notify, accountsToNotify);
      this.exactMatchFound = exactMatchFound;
    }

    void gatherResults() throws OrmException, PermissionBackendException {
      if (notes == null || caller == null) {
        // When notes or caller is missing this is likely just carrying an error message
        // in the contained AddReviewerResult.
        return;
      }

      ChangeData cd = changeDataFactory.create(dbProvider.get(), notes);
      PermissionBackend.ForChange perm =
          permissionBackend.user(caller).database(dbProvider).change(cd);

      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      PostReviewersOp.Result opResult = op.getResult();
      if (migration.readChanges() && state == CC) {
        result.ccs = Lists.newArrayListWithCapacity(opResult.addedCCs().size());
        for (Account.Id accountId : opResult.addedCCs()) {
          IdentifiedUser u = identifiedUserFactory.create(accountId);
          result.ccs.add(json.format(new ReviewerInfo(accountId.get()), perm.user(u), cd));
        }
        accountLoaderFactory.create(true).fill(result.ccs);
        for (Address a : reviewersByEmail) {
          result.ccs.add(new AccountInfo(a.getName(), a.getEmail()));
        }
      } else {
        result.reviewers = Lists.newArrayListWithCapacity(opResult.addedReviewers().size());
        for (PatchSetApproval psa : opResult.addedReviewers()) {
          // New reviewers have value 0, don't bother normalizing.
          IdentifiedUser u = identifiedUserFactory.create(psa.getAccountId());
          result.reviewers.add(
              json.format(
                  new ReviewerInfo(psa.getAccountId().get()),
                  perm.user(u),
                  cd,
                  ImmutableList.of(psa)));
        }
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
