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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.PostReviewers.Input;
import com.google.gerrit.server.change.ReviewerJson.PostResult;
import com.google.gerrit.server.change.ReviewerJson.ReviewerInfo;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Set;

public class PostReviewers implements RestModifyView<ChangeResource, Input> {
  private static final Logger log = LoggerFactory
      .getLogger(PostReviewers.class);

  public static final int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public static final int DEFAULT_MAX_REVIEWERS = 20;

  public static class Input {
    @DefaultInput
    public String reviewer;
    public Boolean confirmed;

    boolean confirmed() {
      return Objects.firstNonNull(confirmed, false);
    }
  }

  private final AccountsCollection accounts;
  private final ReviewerResource.Factory reviewerFactory;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final Provider<GroupsCollection> groupsCollection;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountInfo.Loader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> dbProvider;
  private final IdentifiedUser currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final Config cfg;
  private final ChangeHooks hooks;
  private final AccountCache accountCache;
  private final ReviewerJson json;
  private final ChangeIndexer indexer;

  @Inject
  PostReviewers(AccountsCollection accounts,
      ReviewerResource.Factory reviewerFactory,
      AddReviewerSender.Factory addReviewerSenderFactory,
      Provider<GroupsCollection> groupsCollection,
      GroupMembers.Factory groupMembersFactory,
      AccountInfo.Loader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      IdentifiedUser currentUser,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      @GerritServerConfig Config cfg,
      ChangeHooks hooks,
      AccountCache accountCache,
      ReviewerJson json,
      ChangeIndexer indexer) {
    this.accounts = accounts;
    this.reviewerFactory = reviewerFactory;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.groupsCollection = groupsCollection;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.dbProvider = db;
    this.currentUser = currentUser;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.hooks = hooks;
    this.accountCache = accountCache;
    this.json = json;
    this.indexer = indexer;
  }

  @Override
  public PostResult apply(ChangeResource rsrc, Input input)
      throws BadRequestException, ResourceNotFoundException, AuthException,
      UnprocessableEntityException, OrmException, EmailException, IOException {
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
      EmailException, IOException {
    PostResult result = new PostResult();
    addReviewers(rsrc, result, ImmutableSet.of(rsrc.getUser()));
    return result;
  }

  private PostResult putGroup(ChangeResource rsrc, Input input)
      throws BadRequestException,
      UnprocessableEntityException, OrmException, EmailException, IOException {
    GroupDescription.Basic group = groupsCollection.get().parseInternal(input.reviewer);
    PostResult result = new PostResult();
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      result.error = MessageFormat.format(
          ChangeMessages.get().groupIsNotAllowed, group.getName());
      return result;
    }

    Set<IdentifiedUser> reviewers = Sets.newLinkedHashSet();
    ChangeControl control = rsrc.getControl();
    Set<Account> members;
    try {
      members = groupMembersFactory.create(control.getCurrentUser()).listAccounts(
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
      if (member.isActive()) {
        IdentifiedUser user = identifiedUserFactory.create(member.getId());
        // Does not account for draft status as a user might want to let a
        // reviewer see a draft.
        if (control.forUser(user).isRefVisible()) {
          reviewers.add(user);
        }
      }
    }

    addReviewers(rsrc, result, reviewers);
    return result;
  }

  private void addReviewers(ChangeResource rsrc, PostResult result,
      Set<IdentifiedUser> reviewers)
      throws OrmException, EmailException, IOException {
    if (reviewers.isEmpty()) {
      result.reviewers = ImmutableList.of();
      return;
    }

    ReviewDb db = dbProvider.get();
    PatchSet.Id psid = rsrc.getChange().currentPatchSetId();
    Set<Account.Id> existing = Sets.newHashSet();
    for (PatchSetApproval psa : db.patchSetApprovals().byPatchSet(psid)) {
      existing.add(psa.getAccountId());
    }

    result.reviewers = Lists.newArrayListWithCapacity(reviewers.size());
    List<PatchSetApproval> toInsert =
        Lists.newArrayListWithCapacity(reviewers.size());
    for (IdentifiedUser user : reviewers) {
      Account.Id id = user.getAccountId();
      if (existing.contains(id)) {
        continue;
      }
      ChangeControl control = rsrc.getControl().forUser(user);
      PatchSetApproval psa = dummyApproval(control, psid, id);
      result.reviewers.add(json.format(
          new ReviewerInfo(id), control, ImmutableList.of(psa)));
      toInsert.add(psa);
    }
    if (toInsert.isEmpty()) {
      return;
    }

    db.changes().beginTransaction(rsrc.getChange().getId());
    try {
      ChangeUtil.bumpRowVersionNotLastUpdatedOn(rsrc.getChange().getId(), db);
      db.patchSetApprovals().insert(toInsert);
      db.commit();
    } finally {
      db.rollback();
    }

    CheckedFuture<?, IOException> indexFuture = indexer.indexAsync(rsrc.getChange());
    accountLoaderFactory.create(true).fill(result.reviewers);
    postAdd(rsrc.getChange(), result);
    indexFuture.checkedGet();
  }

  private void postAdd(Change change, PostResult result)
      throws OrmException, EmailException {
    if (result.reviewers.isEmpty()) {
      return;
    }

    // Execute hook for added reviewers
    //
    PatchSet patchSet = dbProvider.get().patchSets().get(change.currentPatchSetId());
    for (AccountInfo info : result.reviewers) {
      Account account = accountCache.get(info._id).getAccount();
      hooks.doReviewerAddedHook(change, account, patchSet, dbProvider.get());
    }

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    List<Account.Id> added =
        Lists.newArrayListWithCapacity(result.reviewers.size());
    for (AccountInfo info : result.reviewers) {
      if (!info._id.equals(currentUser.getAccountId())) {
        added.add(info._id);
      }
    }
    if (!added.isEmpty()) {
      try {
        AddReviewerSender cm = addReviewerSenderFactory.create(change);
        cm.setFrom(currentUser.getAccountId());
        cm.addReviewers(added);
        cm.send();
      } catch (Exception err) {
        log.error("Cannot send email to new reviewers of change "
            + change.getId(), err);
      }
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !(AccountGroup.ANONYMOUS_USERS.equals(groupUUID)
             || AccountGroup.REGISTERED_USERS.equals(groupUUID));
  }

  private PatchSetApproval dummyApproval(ChangeControl ctl,
      PatchSet.Id patchSetId, Account.Id reviewerId) {
    LabelId id =
        Iterables.getLast(ctl.getLabelTypes().getLabelTypes()).getLabelId();
    PatchSetApproval dummyApproval = new PatchSetApproval(
        new PatchSetApproval.Key(patchSetId, reviewerId, id), (short) 0,
        TimeUtil.nowTs());
    dummyApproval.cache(ctl.getChange());
    return dummyApproval;
  }
}
