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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.topic.AddTopicReviewer;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;

class AddTopicReviewerHandler extends Handler<TopicReviewerResult> {
  interface Factory {
    AddTopicReviewerHandler create(Topic.Id topicId, Collection<String> reviewers,
        boolean confirmed);
  }

  private final AddTopicReviewer.Factory addReviewerFactory;
  private final TopicDetailFactory.Factory topicDetailFactory;

  private final Topic.Id topicId;
  private final Collection<String> reviewers;
  private final boolean confirmed;

  @Inject
  AddTopicReviewerHandler(final AddTopicReviewer.Factory addReviewerFactory,
      final TopicDetailFactory.Factory topicDetailFactory,
      @Assisted final Topic.Id topicId,
      @Assisted final Collection<String> reviewers,
      @Assisted final boolean confirmed) {

    this.addReviewerFactory = addReviewerFactory;
    this.topicDetailFactory = topicDetailFactory;

    this.topicId = topicId;
    this.reviewers = reviewers;
    this.confirmed = confirmed;
  }

  @Override
  public TopicReviewerResult call() throws Exception {
    TopicReviewerResult result = addReviewerFactory.create(topicId, reviewers, confirmed).call();
    result.setTopic(topicDetailFactory.create(topicId).call());
    return result;
  }
}
