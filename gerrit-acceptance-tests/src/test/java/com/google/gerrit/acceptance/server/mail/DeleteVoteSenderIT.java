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
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Before;
import org.junit.Test;

public class DeleteVoteSenderIT extends AbstractNotificationTest {
  private TestAccount voter;

  @Before
  public void createExtraAccount() throws Exception {
    voter = accountCreator.create("voter", "voter@example.com", "voter");
    setEmailStrategy(EmailStrategy.ENABLED);
  }

  @Test
  public void deleteVoteFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, voter)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(admin);
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, voter)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    setApiUser(admin);
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin, voter)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewersWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, voter)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteVote", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteVote", sc).to(sc.owner).cc(voter).noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNoneWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteVoteFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  private interface Stager {
    StagedChange stage() throws Exception;
  }

  private StagedChange stageChange(Stager stager) throws Exception {
    StagedChange sc = stager.stage();
    setApiUser(voter);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
    return sc;
  }

  protected StagedChange stageReviewableChange() throws Exception {
    return stageChange(() -> super.stageReviewableChange());
  }

  protected StagedChange stageReviewableWipChange() throws Exception {
    return stageChange(() -> super.stageReviewableWipChange());
  }

  protected StagedChange stageWipChange() throws Exception {
    return stageChange(() -> super.stageWipChange());
  }

  private void deleteVote(StagedChange sc, TestAccount account) throws Exception {
    sender.clear();
    gApi.changes().id(sc.changeId).reviewer(account.email).deleteVote("Code-Review");
  }

  private void deleteVote(StagedChange sc, TestAccount account, NotifyHandling notify)
      throws Exception {
    sender.clear();
    DeleteVoteInput in = new DeleteVoteInput();
    in.label = "Code-Review";
    in.notify = notify;
    gApi.changes().id(sc.changeId).reviewer(account.email).deleteVote(in);
  }

  private void selfCc() throws Exception {
    setEmailStrategy(EmailStrategy.CC_ON_OWN_COMMENTS);
  }

  private void setEmailStrategy(EmailStrategy strategy) throws Exception {
    setEmailStrategy(voter, strategy);
  }
}
