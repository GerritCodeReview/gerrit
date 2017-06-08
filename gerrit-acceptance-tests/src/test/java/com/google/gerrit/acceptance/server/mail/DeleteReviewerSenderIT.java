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
import org.junit.Before;
import org.junit.Test;

public class DeleteReviewerSenderIT extends AbstractNotificationTest {
  private TestAccount extraReviewer;
  private TestAccount extraCcer;

  @Before
  public void createExtraAccount() throws Exception {
    extraReviewer =
        accountCreator.create("extraReviewer", "extraReviewer@example.com", "extraReviewer");
    extraCcer = accountCreator.create("extraCcer", "extraCcer@example.com", "extraCcer");
  }

  @Test
  public void deleteReviewerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(admin, extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteCcerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraCcer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraCcer)
        .cc(extraReviewer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteReviewer", sc).to(extraReviewer).noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteReviewer", sc).to(sc.owner, extraReviewer).noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.ALL);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    setApiUser(extraReviewer);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
    sender.clear();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer);
    assertThat(sender).sent("deleteReviewer", sc).to(extraReviewer).noOneElse();
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
    StagedChange sc = stageWipChangeWithExtraReviewer();
    gApi.changes().id(sc.changeId).reviewer(sc.reviewerByEmail).remove();
    assertThat(sender).notSent();
  }

  private interface Stager {
    StagedChange stage() throws Exception;
  }

  private StagedChange stageChangeWithExtraReviewer(Stager stager) throws Exception {
    StagedChange sc = stager.stage();
    ReviewInput in =
        ReviewInput.noScore()
            .reviewer(extraReviewer.email)
            .reviewer(extraCcer.email, ReviewerState.CC, false);
    gApi.changes().id(sc.changeId).revision("current").review(in);
    return sc;
  }

  private StagedChange stageReviewableChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageReviewableChange);
  }

  private StagedChange stageReviewableWipChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageReviewableWipChange);
  }

  private StagedChange stageWipChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageWipChange);
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
