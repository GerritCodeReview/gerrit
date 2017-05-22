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

import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import org.junit.Test;

public class ReplacePatchSetSenderIT extends AbstractNotificationTest {
  @Test
  public void newPatchSetByOwnerOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange(NEW_PATCHSETS);
    pushTo(sc, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .to(sc.reviewerByEmail)
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS);
  }

  private void pushTo(StagedChange sc, TestAccount by) throws Exception {
    pushFactory
        .create(db, by.getIdent(), sc.repo, sc.changeId)
        .to("refs/for/master")
        .assertOkStatus();
  }
}
