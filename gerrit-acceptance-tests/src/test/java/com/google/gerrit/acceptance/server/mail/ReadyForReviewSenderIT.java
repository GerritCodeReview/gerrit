// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Test;

public class ReadyForReviewSenderIT extends AbstractNotificationTest {
  @Test
  public void setReadyOnWipChange() throws Exception {
    StagedChange sc = stageWipChange(ALL_COMMENTS);
    gApi.changes().id(sc.changeId).setReadyForReview();
    assertThat(sender)
        .sent("newchange", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void setReadyOnWipChangeCcingSelf() throws Exception {
    StagedChange sc = stageReviewableWipChange(ALL_COMMENTS);
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    gApi.changes().id(sc.changeId).setReadyForReview();
    assertThat(sender)
        .sent("newchange", sc)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void setReadyOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange(ALL_COMMENTS);
    setEmailStrategy(sc.owner, EmailStrategy.ENABLED);
    gApi.changes().id(sc.changeId).setReadyForReview();
    assertThat(sender)
        .sent("newchange", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void setWipOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    gApi.changes().id(sc.changeId).setWorkInProgress();
    assertThat(sender).notSent();
  }
}
