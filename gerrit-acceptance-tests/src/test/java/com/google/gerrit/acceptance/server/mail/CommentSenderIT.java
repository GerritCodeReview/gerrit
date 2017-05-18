package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.ENABLED;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.REVIEW_STARTED_CHANGES;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Test;

public class CommentSenderIT extends AbstractNotificationTest {
  @Test
  public void commentOnReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    setApiUser(sc.owner);
    review(sc.changeId, ENABLED);
    assertThat(sender).sent("comment", sc)
        .notTo(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void commentOnReviewableChangeByReviewer() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    setApiUser(sc.reviewer);
    review(sc.changeId, ENABLED);
    assertThat(sender).sent("comment", sc)
        .notTo(sc.reviewer)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcedOnOwnComments() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    setApiUser(sc.owner);
    review(sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender).sent("comment", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void commentOnReviewableChangeByReviewerCcedOnOwnComments() throws Exception {
    StagedChange sc = stageReviewableChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    setApiUser(sc.reviewer);
    review(sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender).sent("comment", sc)
        .to(sc.owner)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  private void review(String changeId, EmailStrategy strategy) throws Exception {
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = strategy;
    gApi.accounts().self().setPreferences(prefs);
    gApi.changes().id(changeId).revision("current").review(ReviewInput.recommend());
  }
}
