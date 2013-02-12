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
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupMembers;
import com.google.gerrit.server.change.PostReviewers.Input;
import com.google.gerrit.server.change.ReviewerJson.PutResult;
import com.google.gerrit.server.change.ReviewerJson.ReviewerInfo;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.List;
import java.util.Set;

class PostReviewers implements RestModifyView<ChangeResource, Input> {
  public final static int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public final static int DEFAULT_MAX_REVIEWERS = 20;

  static class Input {
    @DefaultInput
    String reviewer;
    Boolean confirmed;

    boolean confirmed() {
      return Objects.firstNonNull(confirmed, false);
    }
  }

  private final Reviewers.Parser parser;
  private final ReviewerResource.Factory reviewerFactory;
  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final GroupCache groupCache;
  private final GroupMembers.Factory groupMembersFactory;
  private final AccountInfo.Loader.Factory accountLoaderFactory;
  private final Provider<ReviewDb> db;
  private final IdentifiedUser currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ApprovalCategory.Id addReviewerCategoryId;
  private final Config cfg;
  private final ChangeHooks hooks;
  private final AccountCache accountCache;
  private final ReviewerJson json;

  @Inject
  PostReviewers(Reviewers.Parser parser,
      ReviewerResource.Factory reviewerFactory,
      AddReviewerSender.Factory addReviewerSenderFactory,
      GroupCache groupCache,
      GroupMembers.Factory groupMembersFactory,
      AccountInfo.Loader.Factory accountLoaderFactory,
      Provider<ReviewDb> db,
      IdentifiedUser currentUser,
      IdentifiedUser.GenericFactory identifiedUserFactory,
      ApprovalTypes approvalTypes,
      @GerritServerConfig Config cfg,
      ChangeHooks hooks,
      AccountCache accountCache,
      ReviewerJson json) {
    this.parser = parser;
    this.reviewerFactory = reviewerFactory;
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.groupCache = groupCache;
    this.groupMembersFactory = groupMembersFactory;
    this.accountLoaderFactory = accountLoaderFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.identifiedUserFactory = identifiedUserFactory;
    this.cfg = cfg;
    this.hooks = hooks;
    this.accountCache = accountCache;
    this.json = json;

    this.addReviewerCategoryId = Iterables.getLast(
        approvalTypes.getApprovalTypes()).getCategory().getId();
  }

  @Override
  public PutResult apply(ChangeResource rsrc, Input input)
      throws ResourceNotFoundException, AuthException, NoSuchGroupException,
      NoSuchProjectException, OrmException, EmailException,
      NoSuchChangeException {
    Account.Id accountId = parser.parse(rsrc, input.reviewer);
    if (accountId != null) {
      return putAccount(reviewerFactory.create(rsrc, accountId));
    } else {
      return putGroup(rsrc, input);
    }
  }

  private PutResult putAccount(ReviewerResource rsrc) throws OrmException,
      EmailException, NoSuchChangeException {
    PutResult result = new PutResult();
    addReviewers(rsrc, result, ImmutableSet.of(rsrc.getUser()));
    return result;
  }

  private PutResult putGroup(ChangeResource rsrc, Input input)
      throws ResourceNotFoundException, AuthException, NoSuchGroupException,
      NoSuchProjectException, OrmException, NoSuchChangeException,
      EmailException {
    AccountGroup group =
        groupCache.get(new AccountGroup.NameKey(input.reviewer));
    if (group == null) {
      throw new ResourceNotFoundException(input.reviewer);
    }
    PutResult result = new PutResult();
    if (!isLegalReviewerGroup(group.getGroupUUID())) {
      // TODO: Migrate strings from ChangeMessages
      result.error = String.format(
          "The group %s cannot be added as reviewer.", group.getName());
      return result;
    }

    Set<IdentifiedUser> reviewers = Sets.newLinkedHashSet();
    ChangeControl control = rsrc.getControl();
    Set<Account> members = groupMembersFactory.create(control.getCurrentUser())
        .listAccounts(group.getGroupUUID(), control.getProject().getNameKey());
    if (members == null || members.isEmpty()) {
      return result;
    }

    // if maxAllowed is set to 0, it is allowed to add any number of
    // reviewers
    int maxAllowed =
        cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
    if (maxAllowed > 0 && members.size() > maxAllowed) {
      result.error =
          String.format(
              "The group %s has too many members to add them all as reviewers",
              group.getName());
      return result;
    }

    // if maxWithoutCheck is set to 0, we never ask for confirmation
    int maxWithoutConfirmation =
        cfg.getInt("addreviewer", "maxWithoutConfirmation",
            DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
    if (!input.confirmed() && maxWithoutConfirmation > 0
        && members.size() > maxWithoutConfirmation) {
      result.confirm = true;
      result.error =
          String.format("The group %s has %d members. "
              + "Do you want to add them all as reviewers?", group.getName(),
              members.size());
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

    if (reviewers.isEmpty()) {
      return result;
    }
    addReviewers(rsrc, result, reviewers);
    return result;
  }

  private void addReviewers(ChangeResource rsrc, PutResult result,
      Set<IdentifiedUser> reviewers) throws OrmException, EmailException,
      NoSuchChangeException {
    result.reviewers = Lists.newArrayListWithCapacity(reviewers.size());
    for (IdentifiedUser user : reviewers) {
      ReviewerInfo info =
          addReviewer(rsrc.getControl().forUser(user), user.getAccountId());
      if (info != null) {
        result.reviewers.add(info);
      }
    }
    accountLoaderFactory.create(true).fill(result.reviewers);
    postAdd(rsrc.getChange(), result);
  }

  private ReviewerInfo addReviewer(ChangeControl control, Account.Id id)
      throws OrmException, NoSuchChangeException {
    Change change = control.getChange();
    PatchSet.Id psid = change.currentPatchSetId();
    if (exists(psid, id)) {
      return null;
    }

    // Add the reviewers to the database
    //
    List<PatchSetApproval> toInsert = ImmutableList.of(
        dummyApproval(change, psid, id));
    db.get().patchSetApprovals().insert(toInsert);
    ReviewerInfo info = new ReviewerInfo(id);
    return json.format(info, control, toInsert);
  }

  private void postAdd(Change change, PutResult result)
      throws OrmException, EmailException {
    if (result.reviewers.isEmpty()) {
      return;
    }

    // Execute hook for added reviewers
    //
    PatchSet patchSet = db.get().patchSets().get(change.currentPatchSetId());
    for (AccountInfo info : result.reviewers) {
      Account account = accountCache.get(info._id).getAccount();
      hooks.doReviewerAddedHook(change, account, patchSet, db.get());
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
      AddReviewerSender cm;

      cm = addReviewerSenderFactory.create(change);
      cm.setFrom(currentUser.getAccountId());
      cm.addReviewers(added);
      cm.send();
    }
  }

  public static boolean isLegalReviewerGroup(AccountGroup.UUID groupUUID) {
    return !(AccountGroup.ANONYMOUS_USERS.equals(groupUUID)
             || AccountGroup.REGISTERED_USERS.equals(groupUUID));
  }

  private boolean exists(PatchSet.Id patchSetId, Account.Id reviewerId)
      throws OrmException {
    return !Iterables.isEmpty(
        db.get().patchSetApprovals().byPatchSetUser(patchSetId, reviewerId));
  }

  private PatchSetApproval dummyApproval(Change change, PatchSet.Id patchSetId,
      Account.Id reviewerId) {
    PatchSetApproval dummyApproval =
        new PatchSetApproval(new PatchSetApproval.Key(patchSetId, reviewerId,
            addReviewerCategoryId), (short) 0);
    dummyApproval.cache(change);
    return dummyApproval;
  }
}
