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

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import org.junit.Before;
import org.junit.Test;

public class DeleteReviewerSenderIT extends AbstractNotificationTest {
  private TestAccount extraReviewer;
  private TestAccount extraCcer;

  @Before
  public void createExtraAccount() throws Exception {
    extraReviewer = accounts.create("extraReviewer", "extraReviewer@example.com", "extraReviewer");
    extraCcer = accounts.create("extraCcer", "extraCcer@example.com", "extraCcer");
  }

  @Test
  public void deleteReviewerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .notTo(sc.owner)
        .to(extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(admin, extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteCcerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    removeReviewer(sc, extraCcer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .notTo(sc.owner)
        .to(extraCcer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraReviewer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .notTo(sc.owner)
        .to(extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .notTo(extraCcer, sc.owner, sc.reviewer, sc.ccer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .notTo(extraCcer, sc.reviewer, sc.ccer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    removeReviewer(sc, extraReviewer, NotifyHandling.ALL);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .notTo(sc.owner)
        .to(extraReviewer)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    setApiUser(extraReviewer);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
    sender.clear();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .notTo(sc.owner, sc.ccer, sc.starrer, extraCcer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChangeNotifyOwner() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    setApiUser(extraReviewer);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
    sender.clear();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteReviewer", sc).to(extraReviewer).noOneElse();
  }

  @Test
  public void deleteReviewerByEmailFromWipChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange();
    gApi.changes().id(sc.changeId).reviewer(sc.reviewerByEmail).remove();
    assertThat(sender).notSent();
  }

  private interface Stager {
    StagedChange stage(NotifyType... watches) throws Exception;
  }

  private StagedChange stageChange(Stager stager) throws Exception {
    StagedChange sc = stager.stage(ALL_COMMENTS);
    ReviewInput in =
        ReviewInput.noScore()
            .reviewer(extraReviewer.email)
            .reviewer(extraCcer.email, ReviewerState.CC, false);
    gApi.changes().id(sc.changeId).revision("current").review(in);
    return sc;
  }

  private StagedChange stageReviewableChange() throws Exception {
    return stageChange(this::stageReviewableChange);
  }

  private StagedChange stageReviewableWipChange() throws Exception {
    return stageChange(this::stageReviewableWipChange);
  }

  private StagedChange stageWipChange() throws Exception {
    return stageChange(this::stageWipChange);
  }

  private void removeReviewer(StagedChange sc, TestAccount account) throws Exception {
    sender.clear();
    gApi.changes().id(sc.changeId).reviewer(account.email).remove();
  }

  private void removeReviewer(StagedChange sc, TestAccount account, NotifyHandling notify)
      throws Exception {
    sender.clear();
    DeleteReviewerInput in = new DeleteReviewerInput();
    in.notify = notify;
    gApi.changes().id(sc.changeId).reviewer(account.email).remove(in);
  }
}
