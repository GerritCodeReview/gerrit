// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.topic.RemoveTopicReviewer;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

/**
 * Implement the remote logic that removes a reviewer from a change.
 */
class RemoveTopicReviewerHandler extends Handler<TopicReviewerResult> {
  interface Factory {
    RemoveTopicReviewerHandler create(Topic.Id topicId, Account.Id reviewerId);
  }

  private final RemoveTopicReviewer.Factory removeReviewerFactory;
  private final Account.Id reviewerId;
  private final Topic.Id topicId;
  private final TopicDetailFactory.Factory topicDetailFactory;

  @Inject
  RemoveTopicReviewerHandler(final RemoveTopicReviewer.Factory removeReviewerFactory,
      final TopicDetailFactory.Factory topicDetailFactory,
      @Assisted Topic.Id topicId, @Assisted Account.Id reviewerId) {
    this.removeReviewerFactory = removeReviewerFactory;
    this.topicId = topicId;
    this.reviewerId = reviewerId;
    this.topicDetailFactory = topicDetailFactory;
  }

  @Override
  public TopicReviewerResult call() throws Exception {
    TopicReviewerResult result = removeReviewerFactory.create(
        topicId, Collections.singleton(reviewerId)).call();
    result.setTopic(topicDetailFactory.create(topicId).call());
    return result;
  }

}
