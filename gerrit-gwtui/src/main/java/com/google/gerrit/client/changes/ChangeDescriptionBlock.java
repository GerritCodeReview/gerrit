// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.client.ui.CommentLinkProcessor;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwtexpui.globalkey.client.KeyCommandSet;

public class ChangeDescriptionBlock extends Composite {
  private final ChangeInfoBlock infoBlock;
  private final CommitMessageBlock messageBlock;

  public ChangeDescriptionBlock(KeyCommandSet keysAction) {
    infoBlock = new ChangeInfoBlock();
    messageBlock = new CommitMessageBlock(keysAction);

    final HorizontalPanel hp = new HorizontalPanel();
    hp.add(infoBlock);
    hp.add(messageBlock);
    initWidget(hp);
  }

  public void display(Change chg, Boolean starred, Boolean canEditCommitMessage,
      PatchSetInfo info, AccountInfoCache acc,
      SubmitTypeRecord submitTypeRecord,
      CommentLinkProcessor commentLinkProcessor) {
    infoBlock.display(chg, acc, submitTypeRecord);
    messageBlock.display(chg.currentPatchSetId(), starred,
        canEditCommitMessage, info.getMessage(), commentLinkProcessor);
  }
}
