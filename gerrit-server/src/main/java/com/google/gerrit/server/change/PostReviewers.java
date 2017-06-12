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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
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
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.send.AddReviewerSender;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private final ApprovalsUtil approvalsUtil;
  private final PatchSetUtil psUtil;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final GroupsCollection groupsCollection;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final Provider<IdentifiedUser> user;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final ReviewerJson json;
  private final ReviewerAdded reviewerAdded;
  private final NotesMigration migration;
  private final AccountCache accountCache;

  @Inject
  PostReviewers(
      AccountsCollection accounts,
      ReviewerResource.Factory reviewerFactory,
      ApprovalsUtil approvalsUtil,
      PatchSetUtil psUtil,
      AddReviewerSender.Factory addReviewerSenderFactory,
      GroupsCollection groupsCollection,
      GroupMembers.Factory groupMembersFactory,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      BatchUpdate.Factory batchUpdateFactory,
      Provider<IdentifiedUser> user,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ReviewerJson json,
      ReviewerAdded reviewerAdded,
      NotesMigration migration,
      AccountCache accountCache) {
    this.accounts = accounts;
    this.reviewerFactory = reviewerFactory;
    this.approvalsUtil = approvalsUtil;
    this.psUtil = psUtil;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.groupsCollection = groupsCollection;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.batchUpdateFactory = batchUpdateFactory;
    this.user = user;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.json = json;
    this.reviewerAdded = reviewerAdded;
    this.migration = migration;
    this.accountCache = accountCache;
  }

  @Override
  public AddReviewerResult apply(ChangeResource rsrc, AddReviewerInput input)
      throws IOException, OrmException, RestApiException, UpdateException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    Addition addition = prepareApplication(rsrc, input);
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

  public Addition prepareApplication(ChangeResource rsrc, AddReviewerInput input)
      throws OrmException, RestApiException, IOException {
    Account.Id accountId;
    try {
      accountId = accounts.parse(input.reviewer).getAccountId();
    } catch (UnprocessableEntityException e) {
      try {
        return putGroup(rsrc, input);
      } catch (UnprocessableEntityException e2) {
        throw new UnprocessableEntityException(
            MessageFormat.format(ChangeMessages.get().reviewerNotFound, input.reviewer));
      }
    }
    return putAccount(
        input.reviewer, reviewerFactory.create(rsrc, accountId), input.state(), input.notify);
  }

  Addition ccCurrentUser(CurrentUser user, RevisionResource revision) {
    return new Addition(
        user.getUserName(),
        revision.getChangeResource(),
        ImmutableMap.of(user.getAccountId(), revision.getControl()),
        CC,
        NotifyHandling.NONE);
  }

  private Addition putAccount(
      String reviewer, ReviewerResource rsrc, ReviewerState state, NotifyHandling notify)
      throws UnprocessableEntityException {
    Account member = rsrc.getReviewerUser().getAccount();
    ChangeControl control = rsrc.getReviewerControl();
    if (isValidReviewer(member, control)) {
      return new Addition(
          reviewer,
          rsrc.getChangeResource(),
          ImmutableMap.of(member.getId(), control),
          state,
          notify);
    }
    if (member.isActive()) {
      throw new UnprocessableEntityException(String.format("Change not visible to %s", reviewer));
    }
    throw new UnprocessableEntityException(String.format("Account of %s is inactive.", reviewer));
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

    return new Addition(input.reviewer, rsrc, reviewers, input.state(), input.notify);
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
    final Op op;

    private final Map<Account.Id, ChangeControl> reviewers;

    protected Addition(String reviewer) {
      this(reviewer, null, null, REVIEWER, null);
    }

    protected Addition(
        String reviewer,
        ChangeResource rsrc,
        Map<Account.Id, ChangeControl> reviewers,
        ReviewerState state,
        NotifyHandling notify) {
      result = new AddReviewerResult(reviewer);
      if (reviewers == null) {
        this.reviewers = ImmutableMap.of();
        op = null;
        return;
      }
      this.reviewers = reviewers;
      op = new Op(rsrc, reviewers, state, notify);
    }

    void gatherResults() throws OrmException {
      // Generate result details and fill AccountLoader. This occurs outside
      // the Op because the accounts are in a different table.
      if (migration.readChanges() && op.state == CC) {
        result.ccs = Lists.newArrayListWithCapacity(op.addedCCs.size());
        for (Account.Id accountId : op.addedCCs) {
          result.ccs.add(json.format(new ReviewerInfo(accountId.get()), reviewers.get(accountId)));
        }
        accountLoaderFactory.create(true).fill(result.ccs);
      } else {
        result.reviewers = Lists.newArrayListWithCapacity(op.addedReviewers.size());
        for (PatchSetApproval psa : op.addedReviewers) {
          // New reviewers have value 0, don't bother normalizing.
          result.reviewers.add(
              json.format(
                  new ReviewerInfo(psa.getAccountId().get()),
                  reviewers.get(psa.getAccountId()),
                  ImmutableList.of(psa)));
        }
        accountLoaderFactory.create(true).fill(result.reviewers);
      }
    }
  }

  public class Op extends BatchUpdate.Op {
    final Map<Account.Id, ChangeControl> reviewers;
    final ReviewerState state;
    final NotifyHandling notify;
    List<PatchSetApproval> addedReviewers;
    Collection<Account.Id> addedCCs;

    private final ChangeResource rsrc;
    private PatchSet patchSet;

    Op(
        ChangeResource rsrc,
        Map<Account.Id, ChangeControl> reviewers,
        ReviewerState state,
        NotifyHandling notify) {
      this.rsrc = rsrc;
      this.reviewers = reviewers;
      this.state = state;
      this.notify = notify;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      if (migration.readChanges() && state == CC) {
        addedCCs =
            approvalsUtil.addCcs(
                ctx.getNotes(),
                ctx.getUpdate(ctx.getChange().currentPatchSetId()),
                reviewers.keySet());
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
                reviewers.keySet());
        if (addedReviewers.isEmpty()) {
          return false;
        }
      }

      patchSet = psUtil.current(dbProvider.get(), rsrc.getNotes());
      return true;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      if (addedReviewers != null || addedCCs != null) {
        if (addedReviewers == null) {
          addedReviewers = new ArrayList<>();
        }
        if (addedCCs == null) {
          addedCCs = new ArrayList<>();
        }
        emailReviewers(
            rsrc.getChange(),
            Lists.transform(addedReviewers, r -> r.getAccountId()),
            addedCCs,
            notify);
        if (!addedReviewers.isEmpty()) {
          List<Account> reviewers =
              Lists.transform(
                  addedReviewers, psa -> accountCache.get(psa.getAccountId()).getAccount());
          reviewerAdded.fire(
              rsrc.getChange(), patchSet, reviewers, ctx.getAccount(), ctx.getWhen());
        }
      }
    }
  }

  public void emailReviewers(
      Change change,
      Collection<Account.Id> added,
      Collection<Account.Id> copied,
      NotifyHandling notify) {
    if (added.isEmpty() && copied.isEmpty()) {
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
    if (toMail.isEmpty() && toCopy.isEmpty()) {
      return;
    }

    try {
      AddReviewerSender cm =
          addReviewerSenderFactory.create(change.getProject(), change.getId(), notify);
      cm.setFrom(userId);
      cm.addReviewers(toMail);
      cm.addExtraCC(toCopy);
      cm.send();
    } catch (Exception err) {
      log.error("Cannot send email to new reviewers of change " + change.getId(), err);
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }
}
