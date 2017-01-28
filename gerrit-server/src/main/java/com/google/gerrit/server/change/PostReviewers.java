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

package com.google.gerrit.server.change;

import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.Config;

@Singleton
public class PostReviewers implements RestModifyView<ChangeResource, AddReviewerInput> {

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  private final AccountsCollection accounts;
  private final ReviewerResource.Factory reviewerFactory;
  private final PermissionBackend permissionBackend;

  private final GroupsCollection groupsCollection;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final BatchUpdate.Factory batchUpdateFactory;
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
      ReviewerResource.Factory reviewerFactory,
      PermissionBackend permissionBackend,
      GroupsCollection groupsCollection,
      GroupMembers.Factory groupMembersFactory,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      NotesMigration migration,
      NotifyUtil notifyUtil,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider,
      PostReviewersOp.Factory postReviewersOpFactory,
      OutgoingEmailValidator validator) {
    this.accounts = accounts;
    this.reviewerFactory = reviewerFactory;
    this.permissionBackend = permissionBackend;
    this.groupsCollection = groupsCollection;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.batchUpdateFactory = batchUpdateFactory;
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
  public AddReviewerResult apply(ChangeResource rsrc, AddReviewerInput input)
      throws IOException, OrmException, RestApiException, UpdateException,
          PermissionBackendException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    Addition addition = prepareApplication(rsrc, input, true);
    if (addition.op == null) {
      return addition.result;
    }
    try (BatchUpdate bu =
        batchUpdateFactory.create(
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
      throws OrmException, RestApiException, IOException {
    boolean allowByEmail = projectCache.checkedGet(rsrc.getProject()).isEnableReviewerByEmail();

    Addition byAccountId = addByAccountId(rsrc, input, allowGroup, allowByEmail);
    if (byAccountId != null) {
      return byAccountId;
    }

    Addition wholeGroup = addWholeGroup(rsrc, input, allowGroup, allowByEmail);
    if (wholeGroup != null) {
      return wholeGroup;
    }

    return addByEmail(rsrc, input);
  }

  Addition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new Addition(
        user.getUserName(),
        revision.getChangeResource(),
        ImmutableMap.of(user.getAccountId(), revision.getControl()),
        null,
        CC,
        NotifyHandling.NONE,
        ImmutableListMultimap.of());
  }

  @Nullable
  private Addition addByAccountId(
      ChangeResource rsrc, AddReviewerInput input, boolean allowGroup, boolean allowByEmail)
      throws OrmException, RestApiException {
    Account.Id accountId = null;
    try {
      accountId = accounts.parse(input.reviewer).getAccountId();
    } catch (UnprocessableEntityException e) {
      if (!allowGroup && !allowByEmail) {
        throw new UnprocessableEntityException(
            MessageFormat.format(ChangeMessages.get().reviewerNotFoundUser, input.reviewer));
      }
    }
    if (accountId != null) {
      return putAccount(
          input.reviewer,
          reviewerFactory.create(rsrc, accountId),
          input.state(),
          input.notify,
          notifyUtil.resolveAccounts(input.notifyDetails));
    }
    return null;
  }

  @Nullable
  private Addition addWholeGroup(
      ChangeResource rsrc, AddReviewerInput input, boolean allowGroup, boolean allowByEmail)
      throws OrmException, RestApiException, IOException {
    if (!allowGroup) {
      return null;
    }

    try {
      return putGroup(rsrc, input);
    } catch (UnprocessableEntityException e) {
      if (!allowByEmail) {
        throw new UnprocessableEntityException(
            MessageFormat.format(ChangeMessages.get().reviewerNotFoundUserOrGroup, input.reviewer));
      }
    }
    return null;
  }

  @Nullable
  private Addition addByEmail(ChangeResource rsrc, AddReviewerInput input)
      throws OrmException, RestApiException {
    return putAccountByEmail(
        input.reviewer,
        rsrc,
        input.state(),
        input.notify,
        notifyUtil.resolveAccounts(input.notifyDetails));
  }

  private Addition putAccount(
      String reviewer,
      ReviewerResource rsrc,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws UnprocessableEntityException {
    Account member = rsrc.getReviewerUser().getAccount();
    ChangeControl control = rsrc.getReviewerControl();
    if (isValidReviewer(member, control)) {
      return new Addition(
          reviewer,
          rsrc.getChangeResource(),
          ImmutableMap.of(member.getId(), control),
          null,
          state,
          notify,
          accountsToNotify);
    }
    if (member.isActive()) {
      throw new UnprocessableEntityException(String.format("Change not visible to %s", reviewer));
    }
    throw new UnprocessableEntityException(String.format("Account of %s is inactive.", reviewer));
  }

  private Addition putAccountByEmail(
      String reviewer,
      ChangeResource rsrc,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws UnprocessableEntityException, OrmException, BadRequestException {
    if (!rsrc.getControl().forUser(anonymousProvider.get()).isVisible(dbProvider.get())) {
      throw new BadRequestException("change is not publicly visible");
    }
    if (!migration.readChanges()) {
      throw new BadRequestException("feature only supported in NoteDb");
    }
    Address adr;
    try {
      adr = Address.parse(reviewer);
    } catch (IllegalArgumentException e) {
      throw new UnprocessableEntityException(String.format("email invalid %s", reviewer));
    }
    if (!validator.isValid(adr.getEmail())) {
      throw new UnprocessableEntityException(String.format("email invalid %s", reviewer));
    }
    return new Addition(
        reviewer, rsrc, null, ImmutableList.of(adr), state, notify, accountsToNotify);
  }

  private Addition putGroup(ChangeResource rsrc, AddReviewerInput input)
      throws RestApiException, OrmException, IOException {
    GroupDescription.Basic group = groupsCollection.parseInternal(input.reviewer);
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      return fail(
          input.reviewer,
          MessageFormat.format(ChangeMessages.get().groupIsNotAllowed, group.getName()));
    }

    Map<Account.Id, ChangeControl> reviewers = new HashMap<>();
    ChangeControl control = rsrc.getControl();
    Set<Account> members;
    try {
      members =
          groupMembersFactory
              .create(control.getUser())
              .listAccounts(group.getGroupUUID(), control.getProject().getNameKey());
    } catch (NoSuchGroupException e) {
      throw new UnprocessableEntityException(e.getMessage());
    } catch (NoSuchProjectException e) {
      throw new BadRequestException(e.getMessage());
    }

    // if maxAllowed is set to 0, it is allowed to add any number of
    // reviewers
    int maxAllowed = cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      return fail(
          input.reviewer,
          MessageFormat.format(ChangeMessages.get().groupHasTooManyMembers, group.getName()));
    }

    // if maxWithoutCheck is set to 0, we never ask for confirmation
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation", DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!input.confirmed()
        && maxWithoutConfirmation > 0
        && members.size() > maxWithoutConfirmation) {
      return fail(
          input.reviewer,
          true,
          MessageFormat.format(
              ChangeMessages.get().groupManyMembersConfirmation, group.getName(), members.size()));
    }

    for (Account member : members) {
      if (isValidReviewer(member, control)) {
        reviewers.put(member.getId(), control);
      }
    }

    return new Addition(
        input.reviewer,
        rsrc,
        reviewers,
        null,
        input.state(),
        input.notify,
        notifyUtil.resolveAccounts(input.notifyDetails));
  }

  private boolean isValidReviewer(Account member, ChangeControl control) {
    if (member.isActive()) {
      IdentifiedUser user = identifiedUserFactory.create(member.getId());
      // Does not account for draft status as a user might want to let a
      // reviewer see a draft.
      return control.forUser(user).isRefVisible();
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
    final Map<Account.Id, ChangeControl> reviewers;
    final Collection<Address> reviewersByEmail;
    final ReviewerState state;

    protected Addition(String reviewer) {
      this(reviewer, null, null, null, REVIEWER, null, ImmutableListMultimap.of());
    }

    protected Addition(
        String reviewer,
        ChangeResource rsrc,
        @Nullable Map<Account.Id, ChangeControl> reviewers,
        @Nullable Collection<Address> reviewersByEmail,
        ReviewerState state,
        @Nullable NotifyHandling notify,
        ListMultimap<RecipientType, Account.Id> accountsToNotify) {
      result = new AddReviewerResult(reviewer);
      this.reviewers = reviewers == null ? ImmutableMap.of() : reviewers;
      this.reviewersByEmail = reviewersByEmail == null ? ImmutableList.of() : reviewersByEmail;
      this.state = state;
      if (reviewers == null && reviewersByEmail == null) {
        op = null;
        return;
      }
      op =
          postReviewersOpFactory.create(
              rsrc, this.reviewers, this.reviewersByEmail, state, notify, accountsToNotify);
    }

    void gatherResults() throws OrmException, PermissionBackendException {
      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      PostReviewersOp.Result opResult = op.getResult();
      if (migration.readChanges() && state == CC) {
        result.ccs = Lists.newArrayListWithCapacity(opResult.addedCCs().size());
        for (Account.Id accountId : opResult.addedCCs()) {
          ChangeControl ctl = reviewers.get(accountId);
          PermissionBackend.ForChange perm =
              permissionBackend.user(ctl.getUser()).database(dbProvider).change(ctl.getNotes());
          result.ccs.add(json.format(new ReviewerInfo(accountId.get()), perm, ctl));
        }
        accountLoaderFactory.create(true).fill(result.ccs);
        for (Address a : reviewersByEmail) {
          result.ccs.add(new AccountInfo(a.getName(), a.getEmail()));
        }
      } else {
        result.reviewers = Lists.newArrayListWithCapacity(opResult.addedReviewers().size());
        for (PatchSetApproval psa : opResult.addedReviewers()) {
          // New reviewers have value 0, don't bother normalizing.
          ChangeControl ctl = reviewers.get(psa.getAccountId());
          PermissionBackend.ForChange perm =
              permissionBackend.user(ctl.getUser()).database(dbProvider).change(ctl.getNotes());
          result.reviewers.add(
              json.format(
                  new ReviewerInfo(psa.getAccountId().get()), perm, ctl, ImmutableList.of(psa)));
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
