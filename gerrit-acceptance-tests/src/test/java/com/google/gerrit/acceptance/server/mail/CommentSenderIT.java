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
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.ENABLED;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Test;

public class CommentSenderIT extends AbstractNotificationTest {
  @Test
  public void commentOnReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .notTo(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByReviewer() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.reviewer, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .notTo(sc.reviewer)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.reviewer, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByOther() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(other, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .notTo(other)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByOtherCcingSelf() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(other, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer, other)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(sc.owner, sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).notSent(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).notSent(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnWipChangeByOwner() throws Exception {
    StagedChange sc = stageWipChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnWipChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageWipChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnWipChangeByOwnerNotifyAll() throws Exception {
    StagedChange sc = stageWipChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, ALL);
    assertThat(sender)
        .sent("comment", sc)
        .notTo(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void commentOnReviewableWipChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableWipChange(ALL_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender).notSent();
  }

  private void review(TestAccount account, String changeId, EmailStrategy strategy)
      throws Exception {
    review(account, changeId, strategy, null);
  }

  private void review(
      TestAccount account, String changeId, EmailStrategy strategy, @Nullable NotifyHandling notify)
      throws Exception {
    setEmailStrategy(account, strategy);
    ReviewInput in = ReviewInput.recommend();
    in.notify = notify;
    gApi.changes().id(changeId).revision("current").review(in);
  }
}
