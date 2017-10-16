package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import org.junit.Test;

@UseSsh
public class SetReviewersIT extends AbstractDaemonTest {

  @Test
  public void addByCommitHash() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec(
        "gerrit set-reviewers -a "
            + user.email
            + " "
            + change.getCommit().getId().toString().split("\\s+")[1]);
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertThat(change.getChange().getReviewers().all().contains(user.email));
  }

  @Test
  public void addByChangeID() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec("gerrit set-reviewers -a " + user.email + " " + change.getChangeId());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertThat(change.getChange().getReviewers().all().contains(user.email));
  }

  @Test
  public void removeReviewer() throws Exception {
    PushOneCommit.Result change = createChange();
    adminSshSession.exec("gerrit set-reviewers -a " + user.email + " " + change.getChangeId());
    assert_()
        .withFailureMessage(adminSshSession.getError())
        .that(adminSshSession.hasError())
        .isFalse();
    assertThat(change.getChange().getReviewers().all().contains(user.email));
    adminSshSession.exec("gerrit set-reviewers -r " + user.email + " " + change.getChangeId());
    assertThat(!change.getChange().getReviewers().all().contains(user.email));
  }
}
