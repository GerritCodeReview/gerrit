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
import static com.google.gerrit.extensions.api.changes.NotifyHandling.ALL;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.NONE;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.ENABLED;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ABANDONED_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_CHANGES;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.NEW_PATCHSETS;
import static com.google.gerrit.server.account.WatchConfig.NotifyType.SUBMITTED_CHANGES;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.Util;
import org.junit.Before;
import org.junit.Test;

public class ChangeNotificationsIT extends AbstractNotificationTest {
  private TestAccount other;
  private TestAccount extraReviewer;
  private TestAccount extraCcer;

  @Before
  public void createExtraAccounts() throws Exception {
    extraReviewer =
        accountCreator.create("extraReviewer", "extraReviewer@example.com", "extraReviewer");
    extraCcer = accountCreator.create("extraCcer", "extraCcer@example.com", "extraCcer");
    other = accountCreator.create("other", "other@example.com", "other");
  }

  @Before
  public void grantPermissions() throws Exception {
    grant(project, "refs/*", Permission.FORGE_COMMITTER, false, REGISTERED_USERS);
    grant(project, "refs/*", Permission.SUBMIT, false, REGISTERED_USERS);
    grant(project, "refs/heads/master", Permission.ABANDON, false, REGISTERED_USERS);
    ProjectConfig cfg = projectCache.get(project).getConfig();
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
  }

  @Test
  public void abandonReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  @Test
  public void abandonReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  @Test
  public void abandonReviewableChangeByOther() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    abandon(sc.changeId, other);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  @Test
  public void abandonReviewableChangeNotifyOwnersReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void abandonReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeNotifyOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, OWNER);
    // Self-CC applies *after* need for sending notification is determined.
    // Since there are no recipients before including the user taking action,
    // there should no notification sent.
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS, OWNER);
    assertThat(sender).sent("abandon", sc).to(sc.owner).cc(other).noOneElse();
  }

  @Test
  public void abandonReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableChangeNotifyNoneCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  @Test
  public void abandonWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void abandonWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    abandon(sc.changeId, sc.owner, ALL);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
  }

  private void abandon(String changeId, TestAccount by) throws Exception {
    abandon(changeId, by, ENABLED);
  }

  private void abandon(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    abandon(changeId, by, emailStrategy, null);
  }

  private void abandon(String changeId, TestAccount by, @Nullable NotifyHandling notify)
      throws Exception {
    abandon(changeId, by, ENABLED, notify);
  }

  private void abandon(
      String changeId, TestAccount by, EmailStrategy emailStrategy, @Nullable NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    AbandonInput in = new AbandonInput();
    if (notify != null) {
      in.notify = notify;
    }
    gApi.changes().id(changeId).abandon(in);
  }

  private void addReviewerToReviewableChangeInReviewDb(Adder adder) throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added");
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
    addReviewer(adder, changeId, by, reviewer, ENABLED, null);
  }

  private void addReviewer(
      Adder adder, String changeId, TestAccount by, String reviewer, NotifyHandling notify)
      throws Exception {
    addReviewer(adder, changeId, by, reviewer, ENABLED, notify);
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

  @Test
  public void commentOnReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByReviewer() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.reviewer, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.reviewer, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByOther() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    review(other, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByOtherCcingSelf() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    StagedChange sc = stageReviewableChange();
    review(other, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).notSent(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).notSent(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnWipChangeByOwner() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnWipChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender).notSent();
  }

  @Test
  public void commentOnWipChangeByOwnerNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, ENABLED, ALL);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnWipChangeByBot() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, null, "tag");
    assertThat(sender).sent("comment", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void commentOnReviewableWipChangeByBot() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, null, "tag");
    assertThat(sender).sent("comment", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void commentOnReviewableWipChangeByBotNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, ALL, "tag");
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void commentOnReviewableWipChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  private void review(TestAccount account, String changeId, EmailStrategy strategy)
      throws Exception {
    review(account, changeId, strategy, null);
  }

  private void review(
      TestAccount account, String changeId, EmailStrategy strategy, @Nullable NotifyHandling notify)
      throws Exception {
    review(account, changeId, strategy, notify, null);
  }

  private void review(
      TestAccount account,
      String changeId,
      EmailStrategy strategy,
      @Nullable NotifyHandling notify,
      @Nullable String tag)
      throws Exception {
    setEmailStrategy(account, strategy);
    ReviewInput in = ReviewInput.recommend();
    in.notify = notify;
    in.tag = tag;
    gApi.changes().id(changeId).revision("current").review(in);
  }

  @Test
  public void createReviewableChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void createWipChange() throws Exception {
    stagePreChange("refs/for/master%wip");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyOwnerReviewers() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER_REVIEWERS");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyOwner() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).notSent();
  }

  @Test
  public void createReviewableChangeWithNotifyNone() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).notSent();
  }

  @Test
  public void createWipChangeWithNotifyAll() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master%wip,notify=ALL");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void createReviewableChangeWithReviewersAndCcs() throws Exception {
    // TODO(logan): Support reviewers/CCs-by-email via push option.
    StagedPreChange spc =
        stagePreChange(
            "refs/for/master",
            users -> ImmutableList.of("r=" + users.reviewer.username, "cc=" + users.ccer.username));
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.reviewer, spc.watchingProjectOwner)
        .cc(spc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    setApiUser(admin);
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(admin, extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteCcerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraCcer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraCcer)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteReviewer", sc).to(sc.owner, extraReviewer).noOneElse();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerFromWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer, NotifyHandling.ALL);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    removeReviewer(sc, extraReviewer);
    assertThat(sender).sent("deleteReviewer", sc).to(extraReviewer).noOneElse();
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChangeNotifyOwner() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteReviewerByEmailFromWipChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChangeWithExtraReviewer();
    gApi.changes().id(sc.changeId).reviewer(sc.reviewerByEmail).remove();
    assertThat(sender).notSent();
  }

  private void recommend(StagedChange sc, TestAccount by) throws Exception {
    setApiUser(by);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.recommend());
  }

  private interface Stager {
    StagedChange stage() throws Exception;
  }

  private StagedChange stageChangeWithExtraReviewer(Stager stager) throws Exception {
    StagedChange sc = stager.stage();
    ReviewInput in =
        ReviewInput.noScore()
            .reviewer(extraReviewer.email)
            .reviewer(extraCcer.email, ReviewerState.CC, false);
    setApiUser(extraReviewer);
    gApi.changes().id(sc.changeId).revision("current").review(in);
    return sc;
  }

  private StagedChange stageReviewableChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageReviewableChange);
  }

  private StagedChange stageReviewableWipChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageReviewableWipChange);
  }

  private StagedChange stageWipChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageWipChange);
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

  @Test
  public void deleteVoteFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(admin);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    setApiUser(admin);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewersWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(admin);
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteVote", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNoneWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void deleteVoteFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void deleteVoteFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setApiUser(sc.owner);
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
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

  @Test
  public void mergeByOwner() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("merged", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
  }

  @Test
  public void mergeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
  }

  @Test
  public void mergeByReviewer() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.reviewer);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
  }

  @Test
  public void mergeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.reviewer, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
  }

  @Test
  public void mergeByOtherNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void mergeByOtherNotifyOwner() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, OWNER);
    assertThat(sender).sent("merged", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void mergeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, OWNER);
    assertThat(sender).sent("merged", sc).to(sc.owner).noOneElse();
  }

  @Test
  public void mergeByOtherNotifyNone() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, NONE);
    assertThat(sender).notSent();
  }

  @Test
  public void mergeByOtherCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, NONE);
    assertThat(sender).notSent();
  }

  private void merge(String changeId, TestAccount by) throws Exception {
    merge(changeId, by, ENABLED);
  }

  private void merge(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(changeId).revision("current").submit();
  }

  private void merge(String changeId, TestAccount by, NotifyHandling notify) throws Exception {
    merge(changeId, by, ENABLED, notify);
  }

  private void merge(
      String changeId, TestAccount by, EmailStrategy emailStrategy, NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    SubmitInput in = new SubmitInput();
    in.notify = notify;
    gApi.changes().id(changeId).revision("current").submit(in);
  }

  private StagedChange stageChangeReadyForMerge() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(sc.reviewer);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.approve());
    sender.clear();
    return sc;
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other);
    // TODO(logan): This should include owner but currently doesn't because
    // it's sent *from* the owner.
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, other)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This email shouldn't come from the owner.
        .to(sc.reviewer, sc.ccer, other)
        .notTo(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer, other)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer, sc.ccer, other)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewersInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    // TODO(logan): This should include owner but currently doesn't because
    // it's sent *from* the owner.
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewersInReviewDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    // TODO(logan): This should include owner but currently doesn't because
    // it's sent *from* the owner.
    assertThat(sender).sent("newpatchset", sc).to(sc.reviewer, sc.ccer).noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewersInNoteDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewersInReviewDb()
      throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender).sent("newpatchset", sc).to(sc.owner, sc.reviewer, sc.ccer).noOneElse();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER", other);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=NONE", other);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=NONE", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeToWip() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeNotifyAllInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%wip,notify=ALL", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetOnWipChangeNotifyAllInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%wip,notify=ALL", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetOnWipChangeToReadyInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetOnWipChangeToReadyInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
  }

  @Test
  public void newPatchSetOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnReviewableChangeAddingReviewerInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnReviewableChangeAddingReviewerInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer, newReviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewer() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewerNotifyAllInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%notify=ALL,r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewerNotifyAllInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%notify=ALL,r=" + newReviewer.username, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer, newReviewer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeSettingReadyInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  @Test
  public void newPatchSetOnWipChangeSettingReadyInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).notSent();
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by) throws Exception {
    pushTo(sc, ref, by, ENABLED);
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(sc.owner, emailStrategy);
    pushFactory.create(db, by.getIdent(), sc.repo, sc.changeId).to(ref).assertOkStatus();
  }

  @Test
  public void restoreReviewableChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void restoreReviewableWipChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableWipChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void restoreWipChange() throws Exception {
    StagedChange sc = stageAbandonedWipChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void restoreReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, admin);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void restoreReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  @Test
  public void restoreReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, admin, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(sc.reviewerByEmail, sc.ccerByEmail)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
  }

  private void restore(String changeId, TestAccount by) throws Exception {
    restore(changeId, by, ENABLED);
  }

  private void restore(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(changeId).restore();
  }

  @Test
  public void revertChangeByOwnerInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, sc.owner);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.ccer)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .notTo(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOwnerInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, sc.owner);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.reviewer, sc.ccer, admin)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOwnerCcingSelfInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.ccer)
        .cc(sc.owner)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOwnerCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.owner, sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOtherInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, other);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOtherInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, other);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.owner, sc.reviewer, sc.ccer, admin)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOtherCcingSelfInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageChange();
    revert(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.ccer)
        .cc(other)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .to(other)
        .cc(sc.owner, sc.reviewer, sc.ccer)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  @Test
  public void revertChangeByOtherCcingSelfInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageChange();
    revert(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer, other)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?

    assertThat(sender)
        .sent("revert", sc)
        .to(other)
        .cc(sc.owner, sc.reviewer, sc.ccer, admin)
        .bcc(ALL_COMMENTS)
        .noOneElse(); // TODO(logan): Why not starrer/reviewers-by-email?
  }

  private StagedChange stageChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    setApiUser(admin);
    gApi.changes().id(sc.changeId).revision("current").review(ReviewInput.approve());
    gApi.changes().id(sc.changeId).revision("current").submit();
    sender.clear();
    return sc;
  }

  private void revert(StagedChange sc, TestAccount by) throws Exception {
    revert(sc, by, ENABLED);
  }

  private void revert(StagedChange sc, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    gApi.changes().id(sc.changeId).revert();
  }

  @Test
  public void setAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void setAssigneeOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.owner)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(admin)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void setAssigneeToSelfOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .noOneElse();
  }

  @Test
  public void setAssigneeToSelfOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void changeAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other");
    assign(sc, sc.owner, other);
    sender.clear();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChangeInNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    sender.clear();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .noOneElse();
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChangeInReviewDb() throws Exception {
    assume().that(notesMigration.readChanges()).isFalse();
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    sender.clear();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender).notSent();
  }

  @Test
  public void setAssigneeOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  @Test
  public void setAssigneeOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.reviewerByEmail, sc.ccerByEmail) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to) throws Exception {
    assign(sc, by, to, ENABLED);
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    setApiUser(by);
    AssigneeInput in = new AssigneeInput();
    in.assignee = to.email;
    gApi.changes().id(sc.changeId).setAssignee(in);
  }
}
