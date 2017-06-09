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

import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import org.junit.Test;

public class AddReviewerSenderIT extends AbstractNotificationTest {
  private void addReviewerToReviewableChangeInReviewDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeInReviewDbSingly() throws Exception {
    addReviewerToReviewableChangeInReviewDb(singly());
  }

  @Test
  public void addReviewerToReviewableChangeInReviewDbBatch() throws Exception {
    addReviewerToReviewableChangeInReviewDb(batch());
  }

  private void addReviewerToReviewableChangeInNoteDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbSingly() throws Exception {
    addReviewerToReviewableChangeInNoteDb(singly());
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbBatch() throws Exception {
    addReviewerToReviewableChangeInNoteDb(batch());
  }

  private void addReviewerToReviewableChangeByOwnerCcingSelfInNoteDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, null);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfInNoteDbSingly() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfInNoteDb(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfInNoteDbBatch() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfInNoteDb(batch());
  }

  private void addReviewerToReviewableChangeByOtherInNoteDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    TestAccount other = accounts.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, other, reviewer.email);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeByOtherInNoteDbSingly() throws Exception {
    addReviewerToReviewableChangeByOtherInNoteDb(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOtherInNoteDbBatch() throws Exception {
    addReviewerToReviewableChangeByOtherInNoteDb(batch());
  }

  private void addReviewerToReviewableChangeByOtherCcingSelfInNoteDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    TestAccount other = accounts.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, other, reviewer.email, CC_ON_OWN_COMMENTS, null);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer, other)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeByOtherCcingSelfInNoteDbSingly() throws Exception {
    addReviewerToReviewableChangeByOtherCcingSelfInNoteDb(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOtherCcingSelfInNoteDbBatch() throws Exception {
    addReviewerToReviewableChangeByOtherCcingSelfInNoteDb(batch());
  }

  private void addReviewerByEmailToReviewableChangeInReviewDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    String email = "addedbyemail@example.com";
    StagedChange sc = stageReviewableChange();
    addReviewer(adder, sc.changeId, sc.owner, email);
    assertThat(sender).notSent();
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInReviewDbSingly() throws Exception {
    addReviewerByEmailToReviewableChangeInReviewDb(singly());
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInReviewDbBatch() throws Exception {
    addReviewerByEmailToReviewableChangeInReviewDb(batch());
  }

  private void addReviewerByEmailToReviewableChangeInNoteDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    String email = "addedbyemail@example.com";
    StagedChange sc = stageReviewableChange();
    addReviewer(adder, sc.changeId, sc.owner, email);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(email)
        .cc(sc.reviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInNoteDbSingly() throws Exception {
    addReviewerByEmailToReviewableChangeInNoteDb(singly());
  }

  @Test
  public void addReviewerByEmailToReviewableChangeInNoteDbBatch() throws Exception {
    addReviewerByEmailToReviewableChangeInNoteDb(batch());
  }

  private void addReviewerToWipChange(Adder adder) throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
    assertThat(sender).notSent();
  }

  @Test
  public void addReviewerToWipChangeSingly() throws Exception {
    addReviewerToWipChange(singly());
  }

  @Test
  public void addReviewerToWipChangeBatch() throws Exception {
    addReviewerToWipChange(batch());
  }

  private void addReviewerToReviewableWipChange(Adder adder) throws Exception {
    StagedChange sc = stageReviewableWipChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email);
    assertThat(sender).notSent();
  }

  @Test
  public void addReviewerToReviewableWipChangeSingly() throws Exception {
    addReviewerToReviewableWipChange(singly());
  }

  @Test
  public void addReviewerToReviewableWipChangeBatch() throws Exception {
    addReviewerToReviewableWipChange(batch());
  }

  private void addReviewerToWipChangeInNoteDbNotifyAll(Adder adder) throws Exception {
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
        .noOneElse();
  }

  @Test
  public void addReviewerToWipChangeInNoteDbNotifyAllSingly() throws Exception {
    addReviewerToWipChangeInNoteDbNotifyAll(singly());
  }

  @Test
  public void addReviewerToWipChangeInNoteDbNotifyAllBatch() throws Exception {
    addReviewerToWipChangeInNoteDbNotifyAll(batch());
  }

  private void addReviewerToWipChangeInReviewDbNotifyAll(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email, NotifyHandling.ALL);
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToWipChangeInReviewDbNotifyAllSingly() throws Exception {
    addReviewerToWipChangeInReviewDbNotifyAll(singly());
  }

  @Test
  public void addReviewerToWipChangeInReviewDbNotifyAllBatch() throws Exception {
    addReviewerToWipChangeInReviewDbNotifyAll(batch());
  }

  private void addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewers(Adder adder)
      throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email, OWNER_REVIEWERS);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewersSingly() throws Exception {
    addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewers(singly());
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewersBatch() throws Exception {
    addReviewerToReviewableChangeInNoteDbNotifyOwnerReviewers(batch());
  }

  private void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwner(Adder adder)
      throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwnerSingly()
      throws Exception {
    addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwner(singly());
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwnerBatch()
      throws Exception {
    addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyOwner(batch());
  }

  private void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNone(Adder adder)
      throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accounts.create("added", "added@example.com", "added");
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email, CC_ON_OWN_COMMENTS, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNoneSingly()
      throws Exception {
    addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNone(singly());
  }

  @Test
  public void addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNoneBatch()
      throws Exception {
    addReviewerToReviewableChangeInNoteDbByOwnerCcingSelfNotifyNone(batch());
  }

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
