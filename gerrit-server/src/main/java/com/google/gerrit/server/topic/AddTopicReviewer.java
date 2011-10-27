// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.topic;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.TopicUtil;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupMembersFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.AddReviewerSender;
import com.google.gerrit.server.project.TopicControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public class AddTopicReviewer implements Callable<TopicReviewerResult> {
  public final static int DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK = 10;
  public final static int DEFAULT_MAX_REVIEWERS = 20;

  public interface Factory {
    AddTopicReviewer create(Topic.Id topicId,
        Collection<String> userNameOrEmailOrGroupNames, boolean confirmed);
  }

  private final AddReviewerSender.Factory addReviewerSenderFactory;
  private final AccountResolver accountResolver;
  private final GroupCache groupCache;
  private final GroupMembersFactory.Factory groupMembersFactory;
  private final TopicControl.Factory topicControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ApprovalCategory.Id addReviewerCategoryId;
  private final Config cfg;

  private final Topic.Id topicId;
  private final Collection<String> reviewers;
  private final boolean confirmed;

  @Inject
  AddTopicReviewer(final AddReviewerSender.Factory addReviewerSenderFactory,
      final AccountResolver accountResolver, final GroupCache groupCache,
      final GroupMembersFactory.Factory groupMembersFactory,
      final TopicControl.Factory topicControlFactory, final ReviewDb db,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final IdentifiedUser currentUser, final ApprovalTypes approvalTypes,
      final @GerritServerConfig Config cfg, @Assisted final Topic.Id topicId,
      @Assisted final Collection<String> reviewers,
      @Assisted final boolean confirmed) {
    this.addReviewerSenderFactory = addReviewerSenderFactory;
    this.accountResolver = accountResolver;
    this.groupCache = groupCache;
    this.groupMembersFactory = groupMembersFactory;
    this.db = db;
    this.topicControlFactory = topicControlFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.currentUser = currentUser;
    this.cfg = cfg;

    final List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    addReviewerCategoryId =
        allTypes.get(allTypes.size() - 1).getCategory().getId();

    this.topicId = topicId;
    this.reviewers = reviewers;
    this.confirmed = confirmed;
  }

  @Override
  public TopicReviewerResult call() throws Exception {
    final Set<Account.Id> reviewerIds = new HashSet<Account.Id>();
    final TopicControl control = topicControlFactory.validateFor(topicId);

    final TopicReviewerResult result = new TopicReviewerResult();
    for (final String reviewer : reviewers) {
      final Account account = accountResolver.find(reviewer);
      if (account == null) {
        AccountGroup group = groupCache.get(new AccountGroup.NameKey(reviewer));

        if (group == null) {
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.REVIEWER_NOT_FOUND, reviewer));
          continue;
        }

        if (!isLegalReviewerGroup(group.getGroupUUID())) {
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_NOT_ALLOWED, reviewer));
          continue;
        }

        final Set<Account> members =
            groupMembersFactory.create(control.getProject().getNameKey(),
                group.getGroupUUID()).call();
        if (members == null || members.size() == 0) {
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_EMPTY, reviewer));
          continue;
        }

        // if maxAllowed is set to 0, it is allowed to add any number of
        // reviewers
        final int maxAllowed =
            cfg.getInt("addreviewer", "maxAllowed", DEFAULT_MAX_REVIEWERS);
        if (maxAllowed > 0 && members.size() > maxAllowed) {
          result.setMemberCount(members.size());
          result.setAskForConfirmation(false);
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_HAS_TOO_MANY_MEMBERS, reviewer));
          continue;
        }

        // if maxWithoutCheck is set to 0, we never ask for confirmation
        final int maxWithoutConfirmation =
            cfg.getInt("addreviewer", "maxWithoutConfirmation",
                DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK);
        if (!confirmed && maxWithoutConfirmation > 0
            && members.size() > maxWithoutConfirmation) {
          result.setMemberCount(members.size());
          result.setAskForConfirmation(true);
          result.addError(new ReviewerResult.Error(
              ReviewerResult.Error.Type.GROUP_HAS_TOO_MANY_MEMBERS, reviewer));
          continue;
        }

        for (final Account member : members) {
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
            formatUser(account, reviewer)));
        continue;
      }

      final IdentifiedUser user = identifiedUserFactory.create(account.getId());
      if (!control.forUser(user).isVisible()) {
        result.addError(new ReviewerResult.Error(
            ReviewerResult.Error.Type.CHANGE_NOT_VISIBLE,
            formatUser(account, reviewer)));
        continue;
      }

      reviewerIds.add(account.getId());
    }

    if (reviewerIds.isEmpty()) {
      return result;
    }

    TopicUtil.addReviewers(reviewerIds, db, control, addReviewerCategoryId, currentUser, addReviewerSenderFactory);

    return result;
  }

  private String formatUser(Account account, String nameOrEmail) {
    if (nameOrEmail.matches("^[1-9][0-9]*$")) {
      return RemoveTopicReviewer.formatUser(account, nameOrEmail);
    } else {
      return nameOrEmail;
    }
  }

  public static boolean isLegalReviewerGroup(final AccountGroup.UUID groupUUID) {
    return !(AccountGroup.ANONYMOUS_USERS.equals(groupUUID)
             || AccountGroup.REGISTERED_USERS.equals(groupUUID));
  }
}
