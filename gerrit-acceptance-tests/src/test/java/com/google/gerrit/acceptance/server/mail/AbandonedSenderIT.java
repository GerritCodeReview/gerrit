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
    assertThat(sender).sent("abandon", sc)
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
    assertThat(sender).sent("abandon", sc)
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
    assertThat(sender).sent("abandon", sc)
        .notTo(sc.owner, sc.reviewer, sc.ccer, sc.starrer)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .notTo(ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
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
