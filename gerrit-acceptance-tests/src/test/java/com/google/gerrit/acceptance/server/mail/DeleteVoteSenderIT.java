package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.server.account.WatchConfig.NotifyType;
import org.junit.Before;
import org.junit.Test;

public class DeleteVoteSenderIT extends AbstractNotificationTest {
  private TestAccount voter;

  @Before
  public void createExtraAccount() throws Exception {
    voter = accounts.create("voter", "voter@example.com", "voter");
    setEmailStrategy(EmailStrategy.ENABLED);
  }

  @Test
  public void deleteVoteFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .notTo(voter)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromReviewableChangeWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer, voter)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .notTo(voter)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewersWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer, voter)
        .cc(sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    deleteVote(sc, voter, NotifyHandling.OWNER);
    assertThat(sender)
        .sent("deleteVote", sc)
        .notTo(voter)
        .to(sc.owner)
        .notTo(sc.reviewer, sc.ccer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    selfCc();
    deleteVote(sc, voter, NotifyHandling.OWNER);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(voter)
        .notTo(sc.reviewer, sc.ccer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(sc.starrer)
        .notTo(ALL_COMMENTS);
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
        .notTo(voter)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  @Test
  public void deleteVoteFromWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    deleteVote(sc, voter);
    assertThat(sender)
        .sent("deleteVote", sc)
        .notTo(voter)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This should probably be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS);
  }

  private interface Stager {
    StagedChange stage(NotifyType... watches) throws Exception;
  }

  private StagedChange stageChange(Stager stager) throws Exception {
    StagedChange sc = stager.stage(ALL_COMMENTS);
    setApiUser(voter);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
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
    setApiUser(voter);
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = strategy;
    gApi.accounts().self().setPreferences(prefs);
  }
}
