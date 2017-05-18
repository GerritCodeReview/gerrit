package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.extensions.api.changes.RecipientType.BCC;
import static com.google.gerrit.extensions.api.changes.RecipientType.CC;
import static com.google.gerrit.extensions.api.changes.RecipientType.TO;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ABANDONED_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.REVIEW_STARTED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
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
    Notifications notifications =
        notificationsForReviewableChange(abandon(), ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    assertThat(notifications)
        .owner(null)
        .reviewers(CC)
        .reviewersByEmail(TO) // TODO(logan): This is unintentionally TO, should be CC.
        .ccers(CC)
        .ccersByEmail(CC)
        .starrers(BCC)
        .watcher(ABANDONED_CHANGES, BCC)
        .watcher(REVIEW_STARTED_CHANGES, BCC);
  }

  @Test
  public void abandonReviewableChangeNotifyOwnersReviewers() throws Exception {
    Notifications notifications =
        notificationsForReviewableChange(
            abandon(OWNER_REVIEWERS), ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    assertThat(notifications)
        .owner(null)
        .reviewers(CC)
        .reviewersByEmail(TO)
        .ccers(CC)
        .ccersByEmail(CC)
        .starrers(null)
        .watcher(ABANDONED_CHANGES, null)
        .watcher(REVIEW_STARTED_CHANGES, null);
  }

  @Test
  public void abandonReviewableChangeNotifyOwner() throws Exception {
    Notifications notifications =
        notificationsForReviewableChange(abandon(OWNER), ABANDONED_CHANGES, REVIEW_STARTED_CHANGES);
    assertThat(notifications)
        .owner(null)
        .reviewers(null)
        .reviewersByEmail(null)
        .ccers(null)
        .ccersByEmail(null)
        .starrers(null)
        .watcher(ABANDONED_CHANGES, null)
        .watcher(REVIEW_STARTED_CHANGES, null);
  }

  private ChangeInteraction abandon(NotifyHandling notify) {
    return changeId -> {
      AbandonInput in = new AbandonInput();
      in.notify = notify;
      gApi.changes().id(changeId).abandon(in);
    };
  }

  private ChangeInteraction abandon() {
    return changeId -> gApi.changes().id(changeId).abandon();
  }

  @Test
  public void addReviewerToReviewableChange() throws Exception {
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    Notifications notifications = notificationsForReviewableChange(addReviewer(reviewer.email));
    assertThat(notifications)
        .receives(reviewer, TO)
        .owner(null)
        .reviewers(CC)
        // TODO(logan): This is unintentionally TO in NoteDb, should be CC.
        .reviewersByEmail(notesMigration.enabled() ? TO : CC)
        // TODO(logan): CCs should maybe be included in NoteDb.
        .ccers(notesMigration.enabled() ? null : CC)
        .ccersByEmail(CC)
        .starrers(null);
  }

  @Test
  public void addReviewerByEmailToReviewableChange() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    Notifications notifications =
        notificationsForReviewableChange(addReviewer("addedbyemail@example.com"));
    assertThat(notifications)
        .receives("addedbyemail@example.com", TO)
        .owner(null)
        .reviewers(CC)
        .reviewersByEmail(TO) // TODO(logan): This is unintentionally TO, should be CC.
        .ccers(null) // TODO(logan): CCs should maybe be included in NoteDb.
        .ccersByEmail(CC)
        .starrers(null);
  }

  private ChangeInteraction addReviewer(String email) {
    return changeId -> gApi.changes().id(changeId).addReviewer(email);
  }
}
