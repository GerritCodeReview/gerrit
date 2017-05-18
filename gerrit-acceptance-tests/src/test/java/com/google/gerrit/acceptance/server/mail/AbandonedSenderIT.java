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

import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ABANDONED_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.REVIEW_STARTED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import org.junit.Before;
import org.junit.Test;

public class AbandonedSenderIT extends AbstractNotificationTest {
  @Before
  public void grantPermissions() throws Exception {
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
  }

  @Test
  public void abandonReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    abandon(sc.changeId);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeNotifyOwnersReviewers() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    abandon(sc.changeId, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner, sc.starrer)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .notTo(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    abandon(sc.changeId, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    abandon(sc.changeId);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void abandonNonReviewableWipChange() throws Exception {
    StagedChange sc = stageWipChange(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    abandon(sc.changeId);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .bcc(REVIEW_STARTED_CHANGES); // TODO(logan): Fix this to not notify REVIEW_STARTED_CHANGES
  }

  private void abandon(String changeId) throws Exception {
    gApi.changes().id(changeId).abandon();
  }

  private void abandon(String changeId, NotifyHandling notify) throws Exception {
    AbandonInput in = new AbandonInput();
    in.notify = notify;
    gApi.changes().id(changeId).abandon(in);
  }
}
