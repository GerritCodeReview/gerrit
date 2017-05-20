package com.google.gerrit.acceptance.server.mail;

import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.REVIEW_STARTED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.common.data.Permission;
import org.junit.Before;
import org.junit.Test;

public class RestoredSenderIT extends AbstractNotificationTest {
  @Before
  public void grantPermissions() throws Exception {
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
  }

  @Test
  public void restoreReviewableChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    restore(sc.changeId);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void restoreReviewableWipChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableWipChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    restore(sc.changeId);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
  }

  @Test
  public void restoreNonReviewableWipChange() throws Exception {
    StagedChange sc = stageAbandonedWipChange(ALL_COMMENTS, REVIEW_STARTED_CHANGES);
    restore(sc.changeId);
    assertThat(sender)
        .sent("restore", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .to(sc.reviewerByEmail) // TODO(logan): This is unintentionally TO, should be CC.
        .cc(sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .bcc(REVIEW_STARTED_CHANGES); // TODO(logan): Fix this to not notify REVIEW_STARTED_CHANGES
  }

  private void restore(String changeId) throws Exception {
    gApi.changes().id(changeId).restore();
  }
}
