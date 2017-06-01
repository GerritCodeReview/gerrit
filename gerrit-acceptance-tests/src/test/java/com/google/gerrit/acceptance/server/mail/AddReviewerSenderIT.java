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
import static com.google.gerrit.extensions.api.changes.NotifyHandling.NONE;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Test;

public class AddReviewerSenderIT extends AbstractNotificationTest {
  private interface Adder {
    void addReviewer(String changeId, String reviewer, @Nullable NotifyHandling notify)
        throws Exception;
  }

  private Adder singly() {
    return (String changeId, String reviewer, @Nullable NotifyHandling notify) -> {
      AddReviewerInput in = new AddReviewerInput();
      in.reviewer = reviewer;
      if (notify != null) {
        in.notify = notify;
      }
      gApi.changes().id(changeId).addReviewer(in);
    };
  }

  private Adder batch() {
    return (String changeId, String reviewer, @Nullable NotifyHandling notify) -> {
      ReviewInput in = ReviewInput.noScore();
      in.reviewer(reviewer);
      if (notify != null) {
        in.notify = notify;
      }
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
    forAll(
        adder -> {
          assume().that(notesMigration.readChanges()).isFalse();
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.reviewer, sc.ccer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, null);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.owner, sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.starrer);
        });
  }

  @Test
  public void addReviewerToReviewableChangeByOtherInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    TestAccount other = accounts.create("other", "other@example.com", "other");
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, other, reviewer.email);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.owner, sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.starrer, other);
        });
  }

  @Test
  public void addReviewerToReviewableChangeByOtherCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    TestAccount other = accounts.create("other", "other@example.com", "other");
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, other, reviewer.email, CC_ON_OWN_COMMENTS, null);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.owner, sc.reviewer, other)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.starrer);
        });
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    forAll(
        adder -> {
          String email = "addedbyemail@example.com";
          StagedChange sc = stageReviewableChange();
          addReviewer(adder, sc.changeId, sc.owner, email);
          assertThat(sender).notSent();
        });
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          String email = "addedbyemail@example.com";
          StagedChange sc = stageReviewableChange();
          addReviewer(adder, sc.changeId, sc.owner, email);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(email)
              .cc(sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToWipChange() throws Exception {
    forAll(
        adder -> {
          StagedChange sc = stageWipChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
          assertThat(sender).notSent();
        });
  }

  @Test
  public void addReviewerToReviewableWipChange() throws Exception {
    forAll(
        adder -> {
          StagedChange sc = stageReviewableWipChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
          assertThat(sender).notSent();
        });
  }

  @Test
  public void addReviewerToWipChangeInNoteDbNotifyAll() throws Exception {
    forAll(
        adder -> {
          assume().that(notesMigration.readChanges()).isTrue();
          StagedChange sc = stageWipChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, NotifyHandling.ALL);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToWipChangeInReviewDbNotifyAll() throws Exception {
    forAll(
        adder -> {
          assume().that(notesMigration.readChanges()).isFalse();
          StagedChange sc = stageWipChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, NotifyHandling.ALL);
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.reviewer, sc.ccer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewers() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, OWNER_REVIEWERS);
          // TODO(logan): Should CCs be included?
          assertThat(sender)
              .sent("newchange", sc)
              .to(reviewer)
              .cc(sc.reviewer)
              .cc(sc.reviewerByEmail, sc.ccerByEmail)
              .notTo(sc.owner, sc.starrer);
        });
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwner() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, OWNER);
          assertThat(sender).notSent();
        });
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNone() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    forAll(
        adder -> {
          StagedChange sc = stageReviewableChange();
          TestAccount reviewer = accounts.create("added", "added@example.com", "added");
          addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, NONE);
          assertThat(sender).notSent();
        });
  }

  private void addReviewer(Adder adder, String changeId, TestAccount by, String reviewer)
      throws Exception {
    addReviewer(adder, changeId, by, reviewer, EmailStrategy.ENABLED, null);
  }

  private void addReviewer(
      Adder adder, String changeId, TestAccount by, String reviewer, NotifyHandling notify)
      throws Exception {
    addReviewer(adder, changeId, by, reviewer, EmailStrategy.ENABLED, notify);
  }

  private void addReviewer(
      Adder adder,
      String changeId,
      TestAccount by,
      String reviewer,
      EmailStrategy emailStrategy,
      @Nullable NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    adder.addReviewer(changeId, reviewer, notify);
  }
}
