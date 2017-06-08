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

import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractNotificationTest;
import org.junit.Test;

public class CreateChangeSenderIT extends AbstractNotificationTest {
  @Test
  public void createReviewableChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void createWipChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master%wip");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyOwnerReviewers() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER_REVIEWERS");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyOwner() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyNone() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).notSent();
  }

  @Test
  public void createWipChangeWithNotifyAll() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master%wip,notify=ALL");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void createReviewableChangeWithReviewersAndCcs() throws Exception {
    // TODO(logan): Support reviewers/CCs-by-email via push option.
    StagedPreChange spc =
        stagePreChange(
            "refs/for/master",
            users -> ImmutableList.of("r=" + users.reviewer.username, "cc=" + users.ccer.username));
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.reviewer, spc.watchingProjectOwner)
        .cc(spc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }
}
