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

package com.google.gerrit.client.ui;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;

public class BranchTopicLink extends Composite {
  private final HorizontalPanel hp;

  public BranchTopicLink(Project.NameKey project, Change.Status status,
      String branch, String topic, Topic.Id topicId) {
    String text = branch;
    hp = new HorizontalPanel();

    if (topicId != null) {
      final TopicLink tl = new TopicLink(" (" + topic + ")", topicId);
      hp.add(tl);
    } else if ((topic != null) && (topic != "")) {
      text = branch + " (" + topic + ")";
    }

    final BranchLink bl = new BranchLink(text, project, status, branch, topic);
    hp.insert(bl, 0);

    initWidget(hp);
  }
}