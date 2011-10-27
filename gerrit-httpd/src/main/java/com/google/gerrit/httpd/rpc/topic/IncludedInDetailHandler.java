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

import com.google.gerrit.common.data.IncludedInDetail;
import com.google.gerrit.common.errors.InvalidRevisionException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.httpd.rpc.changedetail.IncludedInDetailFactory;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetElement;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchTopicException;
import com.google.gerrit.server.project.TopicControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class IncludedInDetailHandler extends Handler<IncludedInDetail> {

  interface Factory {
    IncludedInDetailHandler create(Topic.Id id);
  }

  private final ReviewDb db;
  private final IncludedInDetailFactory.Factory includedInDetail;
  private final TopicControl.Factory topicControlFactory;
  private final Topic.Id topicId;

  private TopicControl control;

  @Inject
  IncludedInDetailHandler(final ReviewDb db,
      final IncludedInDetailFactory.Factory includedInDetail,
      final TopicControl.Factory topicControlFactory,
      @Assisted final Topic.Id topicId) {
    this.includedInDetail = includedInDetail;
    this.topicControlFactory = topicControlFactory;
    this.topicId = topicId;
    this.db = db;
  }

  @Override
  public IncludedInDetail call() throws OrmException, NoSuchChangeException,
      IOException, InvalidRevisionException, NoSuchTopicException {
    control = topicControlFactory.validateFor(topicId);

    // We need a reference element belonging to the topic
    // the first one is valid
    //
    ChangeSet.Id currentChangeSet = control.getTopic().currChangeSetId();
    ChangeSetElement changeSetElement = db.changeSetElements().byChangeSet(currentChangeSet).toList().get(0);
    IncludedInDetail detail = includedInDetail.create(changeSetElement.getChangeId()).call();
    return detail;
  }
}
