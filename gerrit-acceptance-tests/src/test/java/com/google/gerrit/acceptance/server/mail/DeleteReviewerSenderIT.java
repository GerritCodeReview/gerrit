package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
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
        .notTo(sc.owner)
        .to(extraReviewer)
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
  public void deleteReviewerFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
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
  public void deleteReviewerFromWipChange() throws Exception {
    StagedChange sc = stageWipChange();
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
