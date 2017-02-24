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

import static com.google.common.base.Preconditions.checkNotNull;
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
import com.google.gerrit.common.errors.NoSuchGroupException;
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
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.ReviewerAdded;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PostReviewers implements RestModifyView<ChangeResource, AddReviewerInput> {
  private static final Logger log = LoggerFactory.getLogger(PostReviewers.class);

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  private final AccountsCollection accounts;
  private final ReviewerResource.Factory reviewerFactory;
  private final PermissionBackend permissionBackend;
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final GroupsCollection groupsCollection;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeData.Factory changeDataFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<IdentifiedUser> user;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final ReviewerJson json;
  private final ReviewerAdded reviewerAdded;
  private final NotesMigration migration;
  private final AccountCache accountCache;
  private final NotifyUtil notifyUtil;
  private final ProjectCache projectCache;
  private final Provider<AnonymousUser> anonymousProvider;

  @Inject
  PostReviewers(
      AccountsCollection accounts,
      ReviewerResource.Factory reviewerFactory,
      PermissionBackend permissionBackend,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      AddReviewerSender.Factory addReviewerSenderFactory,
      GroupsCollection groupsCollection,
      GroupMembers.Factory groupMembersFactory,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      BatchUpdate.Factory batchUpdateFactory,
      Provider<IdentifiedUser> user,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      ReviewerAdded reviewerAdded,
      NotesMigration migration,
      AccountCache accountCache,
      NotifyUtil notifyUtil,
      ProjectCache projectCache,
      Provider<AnonymousUser> anonymousProvider) {
    this.accounts = accounts;
    this.reviewerFactory = reviewerFactory;
    this.permissionBackend = permissionBackend;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.groupsCollection = groupsCollection;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.changeDataFactory = changeDataFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.user = user;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.json = json;
    this.reviewerAdded = reviewerAdded;
    this.migration = migration;
    this.accountCache = accountCache;
    this.notifyUtil = notifyUtil;
    this.projectCache = projectCache;
    this.anonymousProvider = anonymousProvider;
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

  // TODO(hiesel) Refactor this as it starts to become unreadable
  public Addition prepareApplication(
      ChangeResource rsrc, AddReviewerInput input, boolean allowGroup)
      throws OrmException, RestApiException, IOException, PermissionBackendException {
    Account.Id accountId;
    try {
      accountId = accounts.parse(input.reviewer).getAccountId();
    } catch (UnprocessableEntityException e) {
      ProjectConfig projectConfig = projectCache.checkedGet(rsrc.getProject()).getConfig();
      if (allowGroup) {
        try {
          return putGroup(rsrc, input);
        } catch (UnprocessableEntityException e2) {
          if (!projectConfig.getEnableReviewerByEmail()) {
            throw new UnprocessableEntityException(
                MessageFormat.format(
                    ChangeMessages.get().reviewerNotFoundUserOrGroup, input.reviewer));
          }
        }
      }
      if (!projectConfig.getEnableReviewerByEmail()) {
        throw new UnprocessableEntityException(
            MessageFormat.format(ChangeMessages.get().reviewerNotFoundUser, input.reviewer));
      }
      return putAccountByEmail(
          input.reviewer,
          rsrc,
          input.state(),
          input.notify,
          notifyUtil.resolveAccounts(input.notifyDetails));
    }
    return putAccount(
        input.reviewer,
        reviewerFactory.create(rsrc, accountId),
        input.state(),
        input.notify,
        notifyUtil.resolveAccounts(input.notifyDetails));
  }

  Addition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new Addition(
        user.getUserName(),
        revision.getChangeResource(),
        ImmutableSet.of(user.getAccountId()),
        null,
        CC,
        NotifyHandling.NONE,
        ImmutableListMultimap.of());
  }

  private Addition putAccount(
      String reviewer,
      ReviewerResource rsrc,
      ReviewerState state,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws UnprocessableEntityException, PermissionBackendException {
    Account member = rsrc.getReviewerUser().getAccount();
    PermissionBackend.ForRef perm =
        permissionBackend.user(rsrc.getReviewerUser()).ref(rsrc.getChange().getDest());
    if (isValidReviewer(member, perm)) {
      return new Addition(
          reviewer,
          rsrc.getChangeResource(),
          ImmutableSet.of(member.getId()),
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
    if (!OutgoingEmailValidator.isValid(adr.getEmail())) {
      throw new UnprocessableEntityException(String.format("email invalid %s", reviewer));
    }
    return new Addition(
        reviewer, rsrc, null, ImmutableList.of(adr), state, notify, accountsToNotify);
  }

  private Addition putGroup(ChangeResource rsrc, AddReviewerInput input)
      throws RestApiException, OrmException, IOException, PermissionBackendException {
    GroupDescription.Basic group = groupsCollection.parseInternal(input.reviewer);
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      return fail(
          input.reviewer,
          MessageFormat.format(ChangeMessages.get().groupIsNotAllowed, group.getName()));
    }

    Set<Account.Id> reviewers = new HashSet<>();
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

    PermissionBackend.ForRef perm = permissionBackend.user(user).ref(rsrc.getChange().getDest());
    for (Account member : members) {
      if (isValidReviewer(member, perm)) {
        reviewers.add(member.getId());
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

  private boolean isValidReviewer(Account member, PermissionBackend.ForRef perm)
      throws PermissionBackendException {
    if (member.isActive()) {
      IdentifiedUser user = identifiedUserFactory.create(member.getId());
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
    final Op op;
    final ChangeNotes notes;
    private final Collection<Address> reviewersByEmail;

    protected Addition(String reviewer) {
      this(reviewer, null, null, null, REVIEWER, null, ImmutableListMultimap.of());
    }

    protected Addition(
        String reviewer,
        ChangeResource rsrc,
        Set<Account.Id> reviewers,
        @Nullable Collection<Address> reviewersByEmail,
        ReviewerState state,
        NotifyHandling notify,
        ListMultimap<RecipientType, Account.Id> accountsToNotify) {
      result = new AddReviewerResult(reviewer);
      this.reviewersByEmail = reviewersByEmail == null ? ImmutableList.of() : reviewersByEmail;
      if (reviewers == null && reviewersByEmail == null) {
        op = null;
        notes = null;
        return;
      }
      op = new Op(rsrc, reviewers, this.reviewersByEmail, state, notify, accountsToNotify);
      notes = rsrc.getNotes();
    }

    void gatherResults() throws OrmException, PermissionBackendException {
      ChangeData cd = changeDataFactory.create(dbProvider.get(), notes);
      PermissionBackend.ForChange perm =
          permissionBackend.user(user).database(dbProvider).change(cd);

      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      if (migration.readChanges() && op.state == CC) {
        result.ccs = Lists.newArrayListWithCapacity(op.addedCCs.size());
        for (Account.Id accountId : op.addedCCs) {
          IdentifiedUser u = identifiedUserFactory.create(accountId);
          result.ccs.add(json.format(new ReviewerInfo(accountId.get()), perm.user(u), cd));
        }
        accountLoaderFactory.create(true).fill(result.ccs);
        for (Address a : reviewersByEmail) {
          result.ccs.add(new AccountInfo(a.getName(), a.getEmail()));
        }
      } else {
        result.reviewers = Lists.newArrayListWithCapacity(op.addedReviewers.size());
        for (PatchSetApproval psa : op.addedReviewers) {
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

  public class Op implements BatchUpdateOp {
    final Set<Account.Id> reviewers;
    final Collection<Address> reviewersByEmail;
    final ReviewerState state;
    final NotifyHandling notify;
    final ListMultimap<RecipientType, Account.Id> accountsToNotify;
    List<PatchSetApproval> addedReviewers = new ArrayList<>();
    Collection<Account.Id> addedCCs = new ArrayList<>();
    Collection<Address> addedCCsByEmail = new ArrayList<>();

    private final ChangeResource rsrc;
    private PatchSet patchSet;

    Op(
        ChangeResource rsrc,
        Set<Account.Id> reviewers,
        Collection<Address> reviewersByEmail,
        ReviewerState state,
        NotifyHandling notify,
        ListMultimap<RecipientType, Account.Id> accountsToNotify) {
      this.rsrc = rsrc;
      this.reviewers = reviewers;
      this.reviewersByEmail = reviewersByEmail;
      this.state = state;
      this.notify = notify;
      this.accountsToNotify = checkNotNull(accountsToNotify);
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      if (!reviewers.isEmpty()) {
        if (migration.readChanges() && state == CC) {
          addedCCs =
              approvalsUtil.addCcs(
                  ctx.getNotes(), ctx.getUpdate(ctx.getChange().currentPatchSetId()), reviewers);
          if (addedCCs.isEmpty()) {
            return false;
          }
        } else {
          addedReviewers =
              approvalsUtil.addReviewers(
                  ctx.getDb(),
                  ctx.getNotes(),
                  ctx.getUpdate(ctx.getChange().currentPatchSetId()),
                  rsrc.getControl().getLabelTypes(),
                  rsrc.getChange(),
                  reviewers);
          if (addedReviewers.isEmpty()) {
            return false;
          }
        }
      }

      for (Address a : reviewersByEmail) {
        ctx.getUpdate(ctx.getChange().currentPatchSetId())
            .putReviewerByEmail(a, ReviewerStateInternal.fromReviewerState(state));
      }

      patchSet = psUtil.current(dbProvider.get(), rsrc.getNotes());
      return true;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      emailReviewers(
          rsrc.getChange(),
          Lists.transform(addedReviewers, r -> r.getAccountId()),
          addedCCs == null ? ImmutableList.of() : addedCCs,
          reviewersByEmail,
          addedCCsByEmail,
          notify,
          accountsToNotify);
      if (!addedReviewers.isEmpty()) {
        List<Account> reviewers =
            Lists.transform(
                addedReviewers, psa -> accountCache.get(psa.getAccountId()).getAccount());
        reviewerAdded.fire(rsrc.getChange(), patchSet, reviewers, ctx.getAccount(), ctx.getWhen());
      }
    }
  }

  public void emailReviewers(
      Change change,
      Collection<Account.Id> added,
      Collection<Account.Id> copied,
      Collection<Address> addedByEmail,
      Collection<Address> copiedByEmail,
      NotifyHandling notify,
      ListMultimap<RecipientType, Account.Id> accountsToNotify) {
    if (added.isEmpty() && copied.isEmpty() && addedByEmail.isEmpty() && copiedByEmail.isEmpty()) {
      return;
    }

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    List<Account.Id> toMail = Lists.newArrayListWithCapacity(added.size());
    Account.Id userId = user.get().getAccountId();
    for (Account.Id id : added) {
      if (!id.equals(userId)) {
        toMail.add(id);
      }
    }
    List<Account.Id> toCopy = Lists.newArrayListWithCapacity(copied.size());
    for (Account.Id id : copied) {
      if (!id.equals(userId)) {
        toCopy.add(id);
      }
    }
    if (toMail.isEmpty() && toCopy.isEmpty() && addedByEmail.isEmpty() && copiedByEmail.isEmpty()) {
      return;
    }

    try {
      AddReviewerSender cm = addReviewerSenderFactory.create(change.getProject(), change.getId());
      if (notify != null) {
        cm.setNotify(notify);
      }
      cm.setAccountsToNotify(accountsToNotify);
      cm.setFrom(userId);
      cm.addReviewers(toMail);
      cm.addReviewersByEmail(addedByEmail);
      cm.addExtraCC(toCopy);
      cm.addExtraCCByEmail(copiedByEmail);
      cm.send();
    } catch (Exception err) {
      log.error("Cannot send email to new reviewers of change " + change.getId(), err);
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }
}
