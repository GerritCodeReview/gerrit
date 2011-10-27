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

package com.google.gerrit.httpd.rpc.topic;

import com.google.gerrit.common.data.ChangeSetDetail;
import com.google.gerrit.common.data.ChangeSetPublishDetail;
import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.data.TopicDetailService;
import com.google.gerrit.common.data.TopicReviewerResult;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.topic.ChangeSetDetailFactory;
import com.google.gerrit.httpd.rpc.topic.ChangeSetPublishDetailFactory;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.topic.PublishTopicComments;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;

import java.util.List;
import java.util.Set;

class TopicDetailServiceImpl implements TopicDetailService {
  private final AddTopicReviewerHandler.Factory addTopicReviewerFactory;
  private final ChangeSetDetailFactory.Factory changeSetDetail;
  private final ChangeSetPublishDetailFactory.Factory changeSetPublishDetail;
  private final PublishTopicComments.Factory publishCommentsFactory;
  private final RemoveTopicReviewerHandler.Factory removeTopicReviewerFactory;
  private final TopicDetailFactory.Factory topicDetail;
  private final IncludedInDetailHandler.Factory includedInDetailHandler;


  @Inject
  TopicDetailServiceImpl(final AddTopicReviewerHandler.Factory addTopicReviewerFactory,
      final ChangeSetDetailFactory.Factory changeSetDetail,
      final ChangeSetPublishDetailFactory.Factory changeSetPublishDetail,
      final PublishTopicComments.Factory publishCommentsFactory,
      final RemoveTopicReviewerHandler.Factory removeTopicReviewerFactory,
      final TopicDetailFactory.Factory topicDetail,
      final IncludedInDetailHandler.Factory includedInDetailHandler) {
    this.addTopicReviewerFactory = addTopicReviewerFactory;
    this.changeSetDetail = changeSetDetail;
    this.changeSetPublishDetail = changeSetPublishDetail;
    this.publishCommentsFactory = publishCommentsFactory;
    this.removeTopicReviewerFactory = removeTopicReviewerFactory;
    this.topicDetail = topicDetail;
    this.includedInDetailHandler = includedInDetailHandler;
  }

  @Override
  public void topicDetail(final Topic.Id id,
      final AsyncCallback<TopicDetail> callback) {
    topicDetail.create(id).to(callback);
  }

  @Override
  public void includedInDetail(final Topic.Id id,
      final AsyncCallback<IncludedInDetail> callback) {
    includedInDetailHandler.create(id).to(callback);
  }

  @Override
  public void changeSetDetail(final ChangeSet.Id idA,
      final AsyncCallback<ChangeSetDetail> callback) {
    changeSetDetail.create(idA).to(callback);
  }

  @Override
  public void changeSetPublishDetail(final ChangeSet.Id id,
      final AsyncCallback<ChangeSetPublishDetail> callback) {
    changeSetPublishDetail.create(id).to(callback);
  }

  @Override
  public void addTopicReviewers(final Topic.Id id, final List<String> reviewers,
      final boolean confirmed, final AsyncCallback<TopicReviewerResult> callback) {
    addTopicReviewerFactory.create(id, reviewers, confirmed).to(callback);
  }

  @Override
  public void removeTopicReviewer(final Topic.Id id, final Account.Id reviewerId,
      final AsyncCallback<TopicReviewerResult> callback) {
    removeTopicReviewerFactory.create(id, reviewerId).to(callback);
  }

  @Override
  public void publishComments(final ChangeSet.Id csid, final String msg,
      final Set<ApprovalCategoryValue.Id> tags,
      final AsyncCallback<VoidResult> callback) {
    Handler.wrap(publishCommentsFactory.create(csid, msg, tags)).to(callback);
  }
}
