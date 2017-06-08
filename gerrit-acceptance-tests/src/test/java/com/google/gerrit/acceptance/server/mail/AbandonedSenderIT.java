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

import static com.google.gerrit.extensions.api.changes.NotifyHandling.ALL;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.NONE;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ABANDONED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class AbandonedSenderIT extends AbstractNotificationTest {
  @Before
  public void grantPermissions() throws Exception {
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
  }

  @Test
  public void abandonReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeByOther() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    TestAccount other = accounts.create("other", "other@example.com", "other");
    abandon(sc.changeId, other);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(other)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    TestAccount other = accounts.create("other", "other@example.com", "other");
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeNotifyOwnersReviewers() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner, sc.starrer)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeNotifyOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, OWNER);
    // Self-CC applies *after* need for sending notification is determined.
    // Since there are no recipients before including the user taking action,
    // there should no notification sent.
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    TestAccount other = accounts.create("other", "other@example.com", "other");
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS, OWNER);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(other)
        .notTo(sc.reviewer, sc.ccer, sc.starrer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ABANDONED_CHANGES);
  }

  @Test
  public void abandonReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeNotifyNoneCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  @Test
  public void abandonWipChange() throws Exception {
    StagedChange sc = stageWipChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange(ABANDONED_CHANGES);
    abandon(sc.changeId, sc.owner, ALL);
    assertThat(sender)
        .sent("abandon", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES);
  }

  private void abandon(String changeId, TestAccount by) throws Exception {
    abandon(changeId, by, EmailStrategy.ENABLED);
  }

  private void abandon(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    abandon(changeId, by, emailStrategy, null);
  }

  private void abandon(String changeId, TestAccount by, @Nullable NotifyHandling notify)
      throws Exception {
    abandon(changeId, by, EmailStrategy.ENABLED, notify);
  }

  private void abandon(
      String changeId, TestAccount by, EmailStrategy emailStrategy, @Nullable NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    AbandonInput in = new AbandonInput();
    if (notify != null) {
      in.notify = notify;
    }
    gApi.changes().id(changeId).abandon(in);
  }
}
