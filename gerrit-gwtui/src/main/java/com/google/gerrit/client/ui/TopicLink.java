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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.TopicScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.core.client.GWT;

/** Link to the open changes of a topic. */
public class TopicLink extends InlineHyperlink {
  private final Topic.Id topicId;

  public static String permalink(final Topic.Id t) {
    return GWT.getHostPageBaseURL() + "/t/" + t.get() + "/";
  }

  public TopicLink(String topic, Topic.Id topicId) {
    super(topic, PageLinks.toTopic(topicId));
    this.topicId = topicId;
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }

  private Screen createScreen() {
    return new TopicScreen(topicId);
  }
}
