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

package com.google.gerrit.client.changes;

import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.ChangeSetInfo;
import com.google.gerrit.reviewdb.Topic;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;

public class TopicDescriptionBlock extends Composite {
  private final TopicInfoBlock infoBlock;
  private final CommitMessageBlock messageBlock;

  public TopicDescriptionBlock() {
    infoBlock = new TopicInfoBlock();
    messageBlock = new CommitMessageBlock();

    final HorizontalPanel hp = new HorizontalPanel();
    hp.add(infoBlock);
    hp.add(messageBlock);
    initWidget(hp);
  }

  public void display(final Topic topic, final ChangeSetInfo info,
      final AccountInfoCache acc) {
    infoBlock.display(topic, acc);
    messageBlock.display(info.getMessage());
  }
}
