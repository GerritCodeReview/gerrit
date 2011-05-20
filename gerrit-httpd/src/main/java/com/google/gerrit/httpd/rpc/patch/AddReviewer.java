// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.patch;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.account.GroupDetailFactory;
import com.google.gerrit.httpd.rpc.changedetail.ChangeDetailFactory;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class AddReviewer extends Handler<ReviewerResult> {
  interface Factory {
    AddReviewer create(Change.Id changeId,
        Collection<String> userNameOrEmailOrGroupNames, boolean confirmed);
  }

  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final AccountResolver accountResolver;
  private final AccountCache accountCache;
  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ApprovalCategory.Id addReviewerCategoryId;
  private final Config cfg;

  private final Change.Id changeId;
  private final Collection<String> reviewers;
  private final boolean confirmed;

  @Inject
  AddReviewer(final AddReviewerSender.Factory addReviewerSenderFactory,
      final AccountResolver accountResolver, final AccountCache accountCache,
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final IdentifiedUser currentUser, final ApprovalTypes approvalTypes,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final @GerritServerConfig Config cfg, @Assisted final Change.Id changeId,
      @Assisted final Collection<String> userNameOrEmailOrGroupNames,
      @Assisted final boolean confirmed) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.accountResolver = accountResolver;
    this.accountCache = accountCache;
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.currentUser = currentUser;
    this.changeDetailFactory = changeDetailFactory;
    this.cfg = cfg;

    final List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    addReviewerCategoryId =
        allTypes.get(allTypes.size() - 1).getCategory().getId();

    this.changeId = changeId;
    this.reviewers = userNameOrEmailOrGroupNames;
    this.confirmed = confirmed;
  }

  @Override
  public ReviewerResult call() throws Exception {
    final Set<Account.Id> reviewerIds = new HashSet<Account.Id>();
    final ChangeControl control = changeControlFactory.validateFor(changeId);

    final ReviewerResult result = new ReviewerResult();
    for (final String userNameOrEmailOrGroupName : reviewers) {
      final Account account = accountResolver.find(userNameOrEmailOrGroupName);
      if (account == null) {
        AccountGroup group =
            groupCache
                .get(new AccountGroup.NameKey(userNameOrEmailOrGroupName));

        if (group == null) {
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.ACCOUNT_OR_GROUP_NOT_FOUND,
              userNameOrEmailOrGroupName));
          continue;
        }

        if (AccountGroup.ANONYMOUS_USERS.equals(group.getGroupUUID())
            || AccountGroup.REGISTERED_USERS.equals(group.getGroupUUID())) {
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_NOT_ALLOWED,
              userNameOrEmailOrGroupName));
          continue;
        }

        final Set<Account> members;
        if (AccountGroup.PROJECT_OWNERS.equals(group.getGroupUUID())) {
          final Set<AccountGroup.UUID> ownerGroups =
              control.getProjectControl().getProjectState().getOwners();

          members = new HashSet<Account>();
          for (AccountGroup.UUID ownerGroup : ownerGroups) {
            final Set<Account> allGroupMembers =
                getAllGroupMembers(groupCache.get(ownerGroup));
            members.addAll(allGroupMembers);
          }
        } else {
          members = getAllGroupMembers(group);
        }

        if (members == null || members.size() == 0) {
          result
              .addError(new ReviewerResult.Error(
                  ReviewerResult.Error.Type.GROUP_EMPTY,
                  userNameOrEmailOrGroupName));
          continue;
        }

        // if maxAllowed is set 0, it is allowed to add any number of reviewer
        final int maxAllowed = cfg.getInt("addreviewer", "maxAllowed", 20);
        if (maxAllowed > 0 && members.size() > maxAllowed) {
          result.setMemberCount(members.size());
          result.setAskForConfirmation(false);
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_HAS_TOO_MANY_MEMBERS,
              userNameOrEmailOrGroupName));
          continue;
        }

        // if maxWithoutCheck is set 0, we never ask for confirmation
        final int maxWithoutConfirmation =
          cfg.getInt("addreviewer", "maxWithoutConfirmation", 10);
        if (!confirmed && maxWithoutConfirmation > 0
            && members.size() > maxWithoutConfirmation) {
          result.setMemberCount(members.size());
          result.setAskForConfirmation(true);
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_HAS_TOO_MANY_MEMBERS,
              userNameOrEmailOrGroupName));
          continue;
        }

        for (Account member : members) {
          if (member.isActive()) {
            final IdentifiedUser user =
                identifiedUserFactory.create(member.getId());
            if (control.forUser(user).isVisible()) {
              reviewerIds.add(member.getId());
            }
          }
        }
        continue;
      }
      if (!account.isActive()) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.ACCOUNT_INACTIVE,
            userNameOrEmailOrGroupName));
        continue;
      }

      final IdentifiedUser user = identifiedUserFactory.create(account.getId());
      if (!control.forUser(user).isVisible()) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.CHANGE_NOT_VISIBLE,
            userNameOrEmailOrGroupName));
        continue;
      }

      reviewerIds.add(account.getId());
    }

    if (reviewerIds.isEmpty()) {
      return result;
    }

    // Add the reviewers to the database
    //
    final Set<Account.Id> added = new HashSet<Account.Id>();
    final List<PatchSetApproval> toInsert = new ArrayList<PatchSetApproval>();
    final PatchSet.Id psid = control.getChange().currentPatchSetId();
    for (final Account.Id reviewer : reviewerIds) {
      if (!exists(psid, reviewer)) {
        // This reviewer has not entered an approval for this change yet.
        //
        final PatchSetApproval myca = dummyApproval(psid, reviewer);
        toInsert.add(myca);
        added.add(reviewer);
      }
    }
    db.patchSetApprovals().insert(toInsert);

    // Email the reviewers
    //
    // The user knows they added themselves, don't bother emailing them.
    added.remove(currentUser.getAccountId());
    if (!added.isEmpty()) {
      final AddReviewerSender cm;

      cm = addReviewerSenderFactory.create(control.getChange());
      cm.setFrom(currentUser.getAccountId());
      cm.addReviewers(added);
      cm.send();
    }

    result.setChange(changeDetailFactory.create(changeId).call());
    return result;
  }

  private Set<Account> getAllGroupMembers(AccountGroup group)
      throws NoSuchGroupException, OrmException {
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    final Set<Account> members =
        new HashSet<Account>(groupDetail.members.size());
    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        members.add(accountCache.get(member.getAccountId()).getAccount());
      }
    }
    if (groupDetail.includes != null) {
      for (AccountGroupInclude groupInclude : groupDetail.includes) {
        members.addAll(getAllGroupMembers(groupCache.get(groupInclude
            .getIncludeId())));
      }
    }
    return members;
  }

  private boolean exists(final PatchSet.Id patchSetId,
      final Account.Id reviewerId) throws OrmException {
    return db.patchSetApprovals().byPatchSetUser(patchSetId, reviewerId)
        .iterator().hasNext();
  }

  private PatchSetApproval dummyApproval(final PatchSet.Id patchSetId,
      final Account.Id reviewerId) {
    return new PatchSetApproval(new PatchSetApproval.Key(patchSetId,
        reviewerId, addReviewerCategoryId), (short) 0);
  }
}
