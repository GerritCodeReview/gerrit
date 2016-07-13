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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
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
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class PostReviewers implements RestModifyView<ChangeResource, AddReviewerInput> {
  private static final Logger log = LoggerFactory
      .getLogger(PostReviewers.class);

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
  private final AccountCache accountCache;
  private final ReviewerJson json;
  private final ReviewerAdded reviewerAdded;

  @Inject
  PostReviewers(AccountsCollection accounts,
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
      AccountCache accountCache,
      ReviewerJson json,
      ReviewerAdded reviewerAdded) {
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
    this.accountCache = accountCache;
    this.json = json;
    this.reviewerAdded = reviewerAdded;
  }

  @Override
  public AddReviewerResult apply(ChangeResource rsrc, AddReviewerInput input)
      throws UpdateException, OrmException, RestApiException, IOException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    try {
      Account.Id accountId = accounts.parse(input.reviewer).getAccountId();
      return putAccount(input.reviewer, reviewerFactory.create(rsrc, accountId));
    } catch (UnprocessableEntityException e) {
      try {
        return putGroup(rsrc, input);
      } catch (UnprocessableEntityException e2) {
        throw new UnprocessableEntityException(MessageFormat.format(
            ChangeMessages.get().reviewerNotFound,
            input.reviewer));
      }
    }
  }

  private AddReviewerResult putAccount(String reviewer, ReviewerResource rsrc)
      throws OrmException, UpdateException, RestApiException {
    Account member = rsrc.getReviewerUser().getAccount();
    ChangeControl control = rsrc.getReviewerControl();
    AddReviewerResult result = new AddReviewerResult(reviewer);
    if (isValidReviewer(member, control)) {
      addReviewers(rsrc.getChangeResource(), result,
          ImmutableMap.of(member.getId(), control));
    }
    return result;
  }

  private AddReviewerResult putGroup(ChangeResource rsrc, AddReviewerInput input)
      throws UpdateException, RestApiException, OrmException, IOException {
    GroupDescription.Basic group = groupsCollection.parseInternal(input.reviewer);
    AddReviewerResult result = new AddReviewerResult(input.reviewer);
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      result.error = MessageFormat.format(
          ChangeMessages.get().groupIsNotAllowed, group.getName());
      return result;
    }

    Map<Account.Id, ChangeControl> reviewers = new HashMap<>();
    ChangeControl control = rsrc.getControl();
    Set<Account> members;
    try {
      members = groupMembersFactory.create(control.getUser()).listAccounts(
          group.getGroupUUID(), control.getProject().getNameKey());
    } catch (NoSuchGroupException e) {
      throw new UnprocessableEntityException(e.getMessage());
    } catch (NoSuchProjectException e) {
      throw new BadRequestException(e.getMessage());
    }

    // if maxAllowed is set to 0, it is allowed to add any number of
    // reviewers
    int maxAllowed =
        cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      result.error = MessageFormat.format(
          ChangeMessages.get().groupHasTooManyMembers, group.getName());
      return result;
    }

    // if maxWithoutCheck is set to 0, we never ask for confirmation
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation",
            DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!input.confirmed() && maxWithoutConfirmation > 0
        && members.size() > maxWithoutConfirmation) {
      result.confirm = true;
      result.error = MessageFormat.format(
          ChangeMessages.get().groupManyMembersConfirmation,
          group.getName(), members.size());
      return result;
    }

    for (Account member : members) {
      if (isValidReviewer(member, control)) {
        reviewers.put(member.getId(), control);
      }
    }

    addReviewers(rsrc, result, reviewers);
    return result;
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


  private void addReviewers(
      ChangeResource rsrc, AddReviewerResult result, Map<Account.Id, ChangeControl> reviewers)
      throws OrmException, RestApiException, UpdateException {
    try (BatchUpdate bu = batchUpdateFactory.create(
            dbProvider.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op(rsrc, reviewers);
      Change.Id id = rsrc.getChange().getId();
      bu.addOp(id, op);
      bu.execute();

      result.reviewers = Lists.newArrayListWithCapacity(op.added.size());
      for (PatchSetApproval psa : op.added) {
        // New reviewers have value 0, don't bother normalizing.
        result.reviewers.add(
          json.format(new ReviewerInfo(psa.getAccountId().get()),
              reviewers.get(psa.getAccountId()),
              ImmutableList.of(psa)));
      }

      // We don't do this inside Op, since the accounts are in a different
      // table.
      accountLoaderFactory.create(true).fill(result.reviewers);
    }
  }

  private class Op extends BatchUpdate.Op {
    private final ChangeResource rsrc;
    private final Map<Account.Id, ChangeControl> reviewers;

    private List<PatchSetApproval> added;
    private PatchSet patchSet;

    Op(ChangeResource rsrc, Map<Account.Id, ChangeControl> reviewers) {
      this.rsrc = rsrc;
      this.reviewers = reviewers;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException, IOException {
      added =
          approvalsUtil.addReviewers(
              ctx.getDb(),
              ctx.getNotes(),
              ctx.getUpdate(ctx.getChange().currentPatchSetId()),
              rsrc.getControl().getLabelTypes(),
              rsrc.getChange(),
              reviewers.keySet());

      if (added.isEmpty()) {
        return false;
      }
      patchSet = psUtil.current(dbProvider.get(), rsrc.getNotes());
      return true;
    }

    @Override
    public void postUpdate(Context ctx) throws Exception {
      emailReviewers(rsrc.getChange(), added);

      if (!added.isEmpty()) {
        for (PatchSetApproval psa : added) {
          Account account = accountCache.get(psa.getAccountId()).getAccount();
          reviewerAdded.fire(rsrc.getChange(), patchSet, account,
              ctx.getAccount(),
              ctx.getWhen());
        }
      }
    }
  }

  private void emailReviewers(Change change, List<PatchSetApproval> added) {
    if (added.isEmpty()) {
      return;
    }

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    List<Account.Id> toMail = Lists.newArrayListWithCapacity(added.size());
    Account.Id userId = user.get().getAccountId();
    for (PatchSetApproval psa : added) {
      if (!psa.getAccountId().equals(userId)) {
        toMail.add(psa.getAccountId());
      }
    }
    if (!toMail.isEmpty()) {
      try {
        AddReviewerSender cm = addReviewerSenderFactory
            .create(change.getProject(), change.getId());
        cm.setFrom(userId);
        cm.addReviewers(toMail);
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email to new reviewers of change "
            + change.getId(), err);
      }
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !SystemGroupBackend.isSystemGroup(groupUUID);
  }
}
