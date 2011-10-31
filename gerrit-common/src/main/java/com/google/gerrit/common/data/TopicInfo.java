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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Topic;

public class TopicInfo extends CommonInfo {
  protected Topic.Id id;
  protected Topic.Key key;

  protected TopicInfo() {
  }

  public TopicInfo(final Topic t) {
    id = t.getId();
    key = t.getKey();
    owner = t.getOwner();
    subject = t.getSubject();
    status = t.getStatus();
    project = new ProjectInfo(t.getProject());
    branch = t.getDest().getShortName();
    topic = t.getTopic();
    lastUpdatedOn = t.getLastUpdatedOn();
    sortKey = t.getSortKey();
  }

  public Topic.Id getId() {
    return id;
  }

  public Topic.Key getKey() {
    return key;
  }
}
