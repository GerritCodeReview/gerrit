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
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.ReviewerJson.PostResult;
import com.google.gerrit.server.change.ReviewerJson.ReviewerInfo;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
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
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final GroupsCollection groupsCollection;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountLoader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<IdentifiedUser> user;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final ChangeHooks hooks;
  private final AccountCache accountCache;
  private final ReviewerJson json;
  private final ChangeIndexer indexer;

  @Inject
  PostReviewers(AccountsCollection accounts,
      ReviewerResource.Factory reviewerFactory,
      ApprovalsUtil approvalsUtil,
      AddReviewerSender.Factory addReviewerSenderFactory,
      GroupsCollection groupsCollection,
      GroupMembers.Factory groupMembersFactory,
      AccountLoader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      ChangeUpdate.Factory updateFactory,
      Provider<IdentifiedUser> user,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ChangeHooks hooks,
      AccountCache accountCache,
      ReviewerJson json,
      ChangeIndexer indexer) {
    this.accounts = accounts;
    this.reviewerFactory = reviewerFactory;
    this.approvalsUtil = approvalsUtil;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.groupsCollection = groupsCollection;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.updateFactory = updateFactory;
    this.user = user;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.hooks = hooks;
    this.accountCache = accountCache;
    this.json = json;
    this.indexer = indexer;
  }

  @Override
  public PostResult apply(ChangeResource rsrc, AddReviewerInput input)
      throws AuthException, BadRequestException, UnprocessableEntityException,
      OrmException, IOException {
    if (input.reviewer == null) {
      throw new BadRequestException("missing reviewer field");
    }

    try {
      Account.Id accountId = accounts.parse(input.reviewer).getAccountId();
      return putAccount(reviewerFactory.create(rsrc, accountId));
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

  private PostResult putAccount(ReviewerResource rsrc) throws OrmException,
      IOException {
    Account member = rsrc.getReviewerUser().getAccount();
    ChangeControl control = rsrc.getControl();
    PostResult result = new PostResult();
    if (isValidReviewer(member, control)) {
      addReviewers(rsrc, result, ImmutableMap.of(member.getId(), control));
    }
    return result;
  }

  private PostResult putGroup(ChangeResource rsrc, AddReviewerInput input)
      throws BadRequestException,
      UnprocessableEntityException, OrmException, IOException {
    GroupDescription.Basic group = groupsCollection.parseInternal(input.reviewer);
    PostResult result = new PostResult();
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      result.error = MessageFormat.format(
          ChangeMessages.get().groupIsNotAllowed, group.getName());
      return result;
    }

    Map<Account.Id, ChangeControl> reviewers = Maps.newHashMap();
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

  private void addReviewers(ChangeResource rsrc, PostResult result,
      Map<Account.Id, ChangeControl> reviewers)
      throws OrmException, IOException {
    ReviewDb db = dbProvider.get();
    ChangeUpdate update = updateFactory.create(rsrc.getControl());
    List<PatchSetApproval> added;
    db.changes().beginTransaction(rsrc.getId());
    try {
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(rsrc.getId(), db);
      added = approvalsUtil.addReviewers(db, rsrc.getNotes(), update,
          rsrc.getControl().getLabelTypes(), rsrc.getChange(),
          reviewers.keySet());
      db.commit();
    } finally {
      db.rollback();
    }

    update.commit();
    CheckedFuture<?, IOException> indexFuture =
        indexer.indexAsync(rsrc.getId());
    result.reviewers = Lists.newArrayListWithCapacity(added.size());
    for (PatchSetApproval psa : added) {
      // New reviewers have value 0, don't bother normalizing.
      result.reviewers.add(json.format(
          new ReviewerInfo(psa.getAccountId()),
          reviewers.get(psa.getAccountId()),
          ImmutableList.of(psa)));
    }
    accountLoaderFactory.create(true).fill(result.reviewers);
    indexFuture.checkedGet();
    emailReviewers(rsrc.getChange(), added);
    if (!added.isEmpty()) {
      PatchSet patchSet = dbProvider.get().patchSets().get(rsrc.getChange().currentPatchSetId());
      for (PatchSetApproval psa : added) {
        Account account = accountCache.get(psa.getAccountId()).getAccount();
        hooks.doReviewerAddedHook(rsrc.getChange(), account, patchSet, dbProvider.get());
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
        AddReviewerSender cm = addReviewerSenderFactory.create(change.getId());
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
