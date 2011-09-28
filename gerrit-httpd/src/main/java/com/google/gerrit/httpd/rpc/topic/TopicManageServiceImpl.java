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

import com.google.gerrit.common.data.TopicDetail;
import com.google.gerrit.common.data.TopicManageService;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

class TopicManageServiceImpl implements TopicManageService {
  private final SubmitAction.Factory submitAction;
  private final AbandonTopic.Factory abandonTopicFactory;
  private final RestoreTopic.Factory restoreTopicFactory;
  private final RevertTopic.Factory revertTopicFactory;

  @Inject
  TopicManageServiceImpl(final SubmitAction.Factory changeSetAction,
      final AbandonTopic.Factory abandonTopicFactory,
      final RestoreTopic.Factory restoreTopicFactory,
      final RevertTopic.Factory revertTopicFactory) {
    this.submitAction = changeSetAction;
    this.abandonTopicFactory = abandonTopicFactory;
    this.restoreTopicFactory = restoreTopicFactory;
    this.revertTopicFactory = revertTopicFactory;
  }

  public void submit(final ChangeSet.Id csid,
      final AsyncCallback<TopicDetail> cb) {
    submitAction.create(csid).to(cb);
  }

  public void abandonTopic(final ChangeSet.Id csid, final String message,
      final AsyncCallback<TopicDetail> cb) {
    abandonTopicFactory.create(csid, message).to(cb);
  }

  public void revertTopic(final ChangeSet.Id csid, final String message,
      final AsyncCallback<TopicDetail> cb) {
    revertTopicFactory.create(csid, message).to(cb);
  }

  public void restoreTopic(final ChangeSet.Id csid, final String message,
      final AsyncCallback<TopicDetail> cb) {
    restoreTopicFactory.create(csid, message).to(cb);
  }
}
