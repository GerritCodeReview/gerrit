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

import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.project.TopicControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Implement the remote logic that removes a reviewer from a topic.
 */
public class RemoveTopicReviewer implements Callable<TopicReviewerResult> {
  private static final Logger log =
      LoggerFactory.getLogger(RemoveTopicReviewer.class);

  public interface Factory {
    RemoveTopicReviewer create(Topic.Id topicId, Set<Account.Id> reviewerId);
  }

  private final TopicControl.Factory topicControlFactory;
  private final ReviewDb db;
  private final AccountCache accountCache;
  private final Set<Account.Id> ids;
  private final Topic.Id topicId;

  @Inject
  RemoveTopicReviewer(final ReviewDb db, final TopicControl.Factory topicControlFactory,
      AccountCache accountCache, @Assisted Topic.Id topicId,
      @Assisted Set<Account.Id> ids) {
    this.db = db;
    this.topicControlFactory = topicControlFactory;
    this.topicId = topicId;
    this.accountCache = accountCache;
    this.ids = ids;
  }

  @Override
  public TopicReviewerResult call() throws Exception {
    TopicReviewerResult result = new TopicReviewerResult();
    TopicControl ctl = topicControlFactory.validateFor(topicId);
    Set<Account.Id> rejected = new HashSet<Account.Id>();

    List<ChangeSetApproval> current = db.changeSetApprovals().byTopic(topicId).toList();
    for (ChangeSetApproval csa : current) {
      Account.Id who = csa.getAccountId();
      if (ids.contains(who) && !ctl.canRemoveReviewer(csa) && rejected.add(who)) {
        result.addError(new TopicReviewerResult.Error(
            TopicReviewerResult.Error.Type.REMOVE_NOT_PERMITTED,
            formatUser(who)));
      }
    }

    List<ChangeSetApproval> toDelete = new ArrayList<ChangeSetApproval>();
    for (ChangeSetApproval csa : current) {
      Account.Id who = csa.getAccountId();
      if (ids.contains(who) && !rejected.contains(who)) {
        toDelete.add(csa);
      }
    }

    try {
      db.changeSetApprovals().delete(toDelete);
    } catch (OrmException err) {
      log.warn("Cannot remove reviewers from change "+topicId, err);
      Set<Account.Id> failed = new HashSet<Account.Id>();
      for (ChangeSetApproval csa : toDelete) {
        failed.add(csa.getAccountId());
      }
      for (Account.Id who : failed) {
        result.addError(new TopicReviewerResult.Error(
            TopicReviewerResult.Error.Type.COULD_NOT_REMOVE,
            formatUser(who)));
      }
    }

    return result;
  }

  private String formatUser(Account.Id who) {
    AccountState state = accountCache.get(who);
    if (state != null) {
      return formatUser(state.getAccount(), who);
    } else {
      return who.toString();
    }
  }

  static String formatUser(Account a, Object fallback) {
    if (a.getFullName() != null && !a.getFullName().isEmpty()) {
      return a.getFullName();
    }

    if (a.getPreferredEmail() != null && !a.getPreferredEmail().isEmpty()) {
      return a.getPreferredEmail();
    }

    if (a.getUserName() != null && a.getUserName().isEmpty()) {
      return a.getUserName();
    }

    return fallback.toString();
  }
}
