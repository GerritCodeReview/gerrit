package com.google.gerrit.acceptance.server.mail;

import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import org.junit.Test;

public class AddReviewerSenderIT extends AbstractNotificationTest {
  private interface Adder {
    void addReviewer(String changeId, String reviewer) throws Exception;
  }

  private Adder singly() {
    return (String changeId, String reviewer) -> gApi.changes().id(changeId).addReviewer(reviewer);
  }

  private Adder batch() {
    return (String changeId, String reviewer) -> {
      ReviewInput in = ReviewInput.noScore();
      in.reviewer(reviewer);
      gApi.changes().id(changeId).revision("current").review(in);
    };
  }

  private interface Tester {
    void test(Adder adder) throws Exception;
  }

  private void forAll(Tester tester) throws Exception {
    for (Adder adder : ImmutableList.of(singly(), batch())) {
      tester.test(adder);
    }
  }

  @Test
  public void addReviewerToReviewableChangeInReviewDb() throws Exception {
    forAll(adder -> {
      assume().that(notesMigration.enabled()).isFalse();
      StagedChange sc = stageReviewableChange();
      TestAccount reviewer = accounts.create("added", "added@example.com", "added");
      adder.addReviewer(sc.changeId, reviewer.email);
      assertThat(sender).sent("newchange", sc)
          .to(reviewer)
          .cc(sc.reviewer, sc.ccer)
          .cc(sc.reviewerByEmail, sc.ccerByEmail)
          .notTo(sc.owner, sc.starrer);
    });
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    forAll(adder -> {
      StagedChange sc = stageReviewableChange();
      TestAccount reviewer = accounts.create("added", "added@example.com", "added");
      addReviewer(singly(), sc.changeId, reviewer.email);
      // TODO(logan): Existing reviewers by email should be CC.
      // TODO(logan): Should CCs be included?
      assertThat(sender).sent("newchange", sc)
          .to(reviewer)
          .to(sc.reviewerByEmail)
          .cc(sc.reviewer)
          .cc(sc.ccerByEmail)
          .notTo(sc.owner, sc.starrer);
    });
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.enabled()).isFalse();
    forAll(adder -> {
      String email = "addedbyemail@example.com";
      StagedChange sc = stageReviewableChange();
      addReviewer(singly(), sc.changeId, email);
      assertThat(sender).notSent();
    });
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    forAll(adder -> {
      String email = "addedbyemail@example.com";
      StagedChange sc = stageReviewableChange();
      addReviewer(singly(), sc.changeId, email);
      // TODO(logan): Existing reviewers by email should be CC.
      // TODO(logan): Should CCs be included?
      assertThat(sender).sent("newchange", sc)
          .to(email, sc.reviewerByEmail)
          .cc(sc.reviewer)
          .cc(sc.ccerByEmail)
          .notTo(sc.owner, sc.starrer);
    });
  }

  private void addReviewer(Adder adder, String changeId, String reviewer) throws Exception {
    adder.addReviewer(changeId, reviewer);
  }
}
