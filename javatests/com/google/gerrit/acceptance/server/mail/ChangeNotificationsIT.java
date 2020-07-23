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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.entities.NotifyConfig.NotifyType.ABANDONED_CHANGES;
import static com.google.gerrit.entities.NotifyConfig.NotifyType.ALL_COMMENTS;
import static com.google.gerrit.entities.NotifyConfig.NotifyType.NEW_CHANGES;
import static com.google.gerrit.entities.NotifyConfig.NotifyType.NEW_PATCHSETS;
import static com.google.gerrit.entities.NotifyConfig.NotifyType.SUBMITTED_CHANGES;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.ALL;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.NONE;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER;
import static com.google.gerrit.extensions.api.changes.NotifyHandling.OWNER_REVIEWERS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.CC_ON_OWN_COMMENTS;
import static com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy.ENABLED;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.gerrit.acceptance.AbstractNotificationTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.CommitMessageInput;
import com.google.gerrit.server.restapi.change.PostReview;
import com.google.inject.Inject;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class ChangeNotificationsIT extends AbstractNotificationTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  /*
   * Set up for extra standard test accounts and permissions.
   */
  private TestAccount other;
  private TestAccount extraReviewer;
  private TestAccount extraCcer;

  @Before
  public void createExtraAccounts() throws Exception {
    extraReviewer =
        accountCreator.create("extraReviewer", "extraReviewer@example.com", "extraReviewer", null);
    extraCcer = accountCreator.create("extraCcer", "extraCcer@example.com", "extraCcer", null);
    other = accountCreator.create("other", "other@example.com", "other", null);
  }

  @Before
  public void grantPermissions() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.FORGE_COMMITTER).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.ABANDON).ref("refs/*").group(REGISTERED_USERS))
        .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(-2, +2))
        .update();
  }

  /*
   * AbandonedSender tests.
   */

  @Test
  public void abandonReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeByOther() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    abandon(sc.changeId, other);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("abandon", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeNotifyOwnersReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, OWNER);
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeNotifyOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, OWNER);
    // Self-CC applies *after* need for sending notification is determined.
    // Since there are no recipients before including the user taking action,
    // there should no notification sent.
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    abandon(sc.changeId, other, CC_ON_OWN_COMMENTS, OWNER);
    assertThat(sender).sent("abandon", sc).to(sc.owner).cc(other).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableChangeNotifyNoneCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    abandon(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    abandon(sc.changeId, sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void abandonWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    abandon(sc.changeId, sc.owner, ALL);
    assertThat(sender)
        .sent("abandon", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ABANDONED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
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
    requestScopeOperations.setApiUser(by.id());
    AbandonInput in = new AbandonInput();
    if (notify != null) {
      in.notify = notify;
    }
    gApi.changes().id(changeId).abandon(in);
  }

  /*
   * AddReviewerSender tests.
   */

  private void addReviewerToReviewableChange(Adder adder) throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email());
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeSingly() throws Exception {
    addReviewerToReviewableChange(singly());
  }

  @Test
  public void addReviewerToReviewableChangeBatch() throws Exception {
    addReviewerToReviewableChange(batch());
  }

  private void addReviewerToReviewableChangeByOwnerCcingSelf(Adder adder) throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email(), CC_ON_OWN_COMMENTS, null);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfSingly() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelf(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfBatch() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelf(batch());
  }

  private void addReviewerToReviewableChangeByOther(Adder adder) throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, other, reviewer.email());
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeByOtherSingly() throws Exception {
    addReviewerToReviewableChangeByOther(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOtherBatch() throws Exception {
    addReviewerToReviewableChangeByOther(batch());
  }

  private void addReviewerToReviewableChangeByOtherCcingSelf(Adder adder) throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, other, reviewer.email(), CC_ON_OWN_COMMENTS, null);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.owner, sc.reviewer, other)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeByOtherCcingSelfSingly() throws Exception {
    addReviewerToReviewableChangeByOtherCcingSelf(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOtherCcingSelfBatch() throws Exception {
    addReviewerToReviewableChangeByOtherCcingSelf(batch());
  }

  private void addReviewerByEmailToReviewableChange(Adder adder) throws Exception {
    String email = "addedbyemail@example.com";
    StagedChange sc = stageReviewableChange();
    addReviewer(adder, sc.changeId, sc.owner, email);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(email)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerByEmailToReviewableChangeSingly() throws Exception {
    addReviewerByEmailToReviewableChange(singly());
  }

  @Test
  public void addReviewerByEmailToReviewableChangeBatch() throws Exception {
    addReviewerByEmailToReviewableChange(batch());
  }

  private void addReviewerToWipChange(Adder adder) throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email());
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToWipChangeSingly() throws Exception {
    addReviewerToWipChange(singly());
  }

  @Test
  public void addReviewerToWipChangeBatch() throws Exception {
    addReviewerToWipChange(batch());
  }

  @Test
  public void addReviewerToReviewableWipChangeSingly() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(singly(), sc.changeId, sc.owner, reviewer.email());
    // TODO(dborowitz): In theory this should match the batch case, but we don't currently pass
    // enough info into AddReviewersEmail#emailReviewers to distinguish the reviewStarted case.
    // Complicating the emailReviewers arguments is not the answer; this needs to be rewritten.
    // Tolerate the difference for now.
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableWipChangeBatch() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(batch(), sc.changeId, sc.owner, reviewer.email());
    // For a review-started WIP change, same as in the notify=ALL case. It's not especially
    // important to notify just because a reviewer is added, but we do want to notify in the other
    // case that hits this codepath: posting an actual review.
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
  }

  private void addReviewerToWipChangeNotifyAll(Adder adder) throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email(), NotifyHandling.ALL);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToWipChangeNotifyAllSingly() throws Exception {
    addReviewerToWipChangeNotifyAll(singly());
  }

  @Test
  public void addReviewerToWipChangeNotifyAllBatch() throws Exception {
    addReviewerToWipChangeNotifyAll(batch());
  }

  private void addReviewerToReviewableChangeNotifyOwnerReviewers(Adder adder) throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email(), OWNER_REVIEWERS);
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(reviewer)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeNotifyOwnerReviewersSingly() throws Exception {
    addReviewerToReviewableChangeNotifyOwnerReviewers(singly());
  }

  @Test
  public void addReviewerToReviewableChangeNotifyOwnerReviewersBatch() throws Exception {
    addReviewerToReviewableChangeNotifyOwnerReviewers(batch());
  }

  private void addReviewerToReviewableChangeByOwnerCcingSelfNotifyOwner(Adder adder)
      throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email(), CC_ON_OWN_COMMENTS, OWNER);
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfNotifyOwnerSingly() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfNotifyOwner(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfNotifyOwnerBatch() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfNotifyOwner(batch());
  }

  private void addReviewerToReviewableChangeByOwnerCcingSelfNotifyNone(Adder adder)
      throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount reviewer = accountCreator.create("added", "added@example.com", "added", null);
    addReviewer(adder, sc.changeId, sc.owner, reviewer.email(), CC_ON_OWN_COMMENTS, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfNotifyNoneSingly() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfNotifyNone(singly());
  }

  @Test
  public void addReviewerToReviewableChangeByOwnerCcingSelfNotifyNoneBatch() throws Exception {
    addReviewerToReviewableChangeByOwnerCcingSelfNotifyNone(batch());
  }

  private void addNonUserReviewerByEmail(Adder adder) throws Exception {
    StagedChange sc = stageReviewableChange();
    addReviewer(adder, sc.changeId, sc.owner, "nonexistent@example.com");
    assertThat(sender)
        .sent("newchange", sc)
        .to("nonexistent@example.com")
        .cc(sc.reviewer)
        .cc(StagedUsers.CC_BY_EMAIL, StagedUsers.REVIEWER_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addNonUserReviewerByEmailSingly() throws Exception {
    addNonUserReviewerByEmail(singly(ReviewerState.REVIEWER));
  }

  @Test
  public void addNonUserReviewerByEmailBatch() throws Exception {
    addNonUserReviewerByEmail(batch(ReviewerState.REVIEWER));
  }

  private void addNonUserCcByEmail(Adder adder) throws Exception {
    StagedChange sc = stageReviewableChange();
    addReviewer(adder, sc.changeId, sc.owner, "nonexistent@example.com");
    assertThat(sender)
        .sent("newchange", sc)
        .cc("nonexistent@example.com")
        .cc(sc.reviewer)
        .cc(StagedUsers.CC_BY_EMAIL, StagedUsers.REVIEWER_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addNonUserCcByEmailSingly() throws Exception {
    addNonUserCcByEmail(singly(ReviewerState.CC));
  }

  @Test
  public void addNonUserCcByEmailBatch() throws Exception {
    addNonUserCcByEmail(batch(ReviewerState.CC));
  }

  private interface Adder {
    void addReviewer(String changeId, String reviewer, @Nullable NotifyHandling notify)
        throws Exception;
  }

  private Adder singly() {
    return singly(ReviewerState.REVIEWER);
  }

  private Adder singly(ReviewerState reviewerState) {
    return (String changeId, String reviewer, @Nullable NotifyHandling notify) -> {
      AddReviewerInput in = new AddReviewerInput();
      in.reviewer = reviewer;
      in.state = reviewerState;
      if (notify != null) {
        in.notify = notify;
      }
      gApi.changes().id(changeId).addReviewer(in);
    };
  }

  private Adder batch() {
    return batch(ReviewerState.REVIEWER);
  }

  private Adder batch(ReviewerState reviewerState) {
    return (String changeId, String reviewer, @Nullable NotifyHandling notify) -> {
      ReviewInput in = ReviewInput.noScore();
      in.reviewer(reviewer, reviewerState, false);
      if (notify != null) {
        in.notify = notify;
      }
      gApi.changes().id(changeId).current().review(in);
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
    requestScopeOperations.setApiUser(by.id());
    adder.addReviewer(changeId, reviewer, notify);
  }

  /*
   * CommentSender tests.
   */

  @Test
  public void commentOnReviewableChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByReviewer() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.reviewer, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.reviewer, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOther() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    StagedChange sc = stageReviewableChange();
    review(other, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOtherCcingSelf() throws Exception {
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    StagedChange sc = stageReviewableChange();
    review(other, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, OWNER);
    assertThat(sender).didNotSend(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnReviewableChangeByOwnerNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    review(sc.owner, sc.changeId, ENABLED, NONE);
    assertThat(sender).didNotSend(); // TODO(logan): Why not send to owner?
  }

  @Test
  public void commentOnReviewableChangeByBot() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, null, "autogenerated:bot");
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnWipChangeByOwner() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnWipChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, CC_ON_OWN_COMMENTS);
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnWipChangeByOwnerNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    review(sc.owner, sc.changeId, ENABLED, ALL);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnWipChangeByBot() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, null, "autogenerated:tag");
    assertThat(sender).sent("comment", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableWipChangeByBot() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    TestAccount bot = sc.testAccount("bot");
    review(bot, sc.changeId, ENABLED, null, "autogenerated:tag");
    assertThat(sender).sent("comment", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
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
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnReviewableWipChangeByOwner() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    review(sc.owner, sc.changeId, ENABLED);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void noCommentAndSetWorkInProgress() throws Exception {
    StagedChange sc = stageReviewableChange();
    ReviewInput in = ReviewInput.noScore().setWorkInProgress(true);
    gApi.changes().id(sc.changeId).current().review(in);
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentAndSetWorkInProgress() throws Exception {
    StagedChange sc = stageReviewableChange();
    ReviewInput in = ReviewInput.noScore().message("ok").setWorkInProgress(true);
    gApi.changes().id(sc.changeId).current().review(in);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void commentOnWipChangeAndStartReview() throws Exception {
    StagedChange sc = stageWipChange();
    ReviewInput in = ReviewInput.noScore().message("ok").setWorkInProgress(false);
    gApi.changes().id(sc.changeId).current().review(in);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void addReviewerOnWipChangeAndStartReview() throws Exception {
    StagedChange sc = stageWipChange();
    ReviewInput in = ReviewInput.noScore().reviewer(other.email()).setWorkInProgress(false);
    gApi.changes().id(sc.changeId).current().review(in);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer, other)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    // TODO(logan): Should CCs be included?
    assertThat(sender)
        .sent("newchange", sc)
        .to(other)
        .cc(sc.reviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void startReviewMessageNotRepeated() throws Exception {
    // TODO(logan): Remove this test check once PolyGerrit workaround is rolled back.
    StagedChange sc = stageWipChange();
    ReviewInput in =
        ReviewInput.noScore().message(PostReview.START_REVIEW_MESSAGE).setWorkInProgress(false);
    gApi.changes().id(sc.changeId).current().review(in);
    Truth.assertThat(sender.getMessages()).isNotEmpty();
    String body = sender.getMessages().get(0).body();
    int idx = body.indexOf(PostReview.START_REVIEW_MESSAGE);
    Truth.assertThat(idx).isAtLeast(0);
    Truth.assertThat(body.indexOf(PostReview.START_REVIEW_MESSAGE, idx + 1)).isEqualTo(-1);
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
    gApi.changes().id(changeId).current().review(in);
  }

  /*
   * CreateChangeSender tests.
   */

  @Test
  public void createReviewableChange() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void createWipChange() throws Exception {
    stagePreChange("refs/for/master%wip");
    assertThat(sender).didNotSend();
  }

  @Test
  public void createWipChangeWithWorkInProgressByDefaultForProject() throws Exception {
    setWorkInProgressByDefault(project, InheritableBoolean.TRUE);
    StagedPreChange spc = stagePreChange("refs/for/master");
    Truth.assertThat(gApi.changes().id(spc.changeId).get().workInProgress).isTrue();
    assertThat(sender).didNotSend();
  }

  @Test
  public void createWipChangeWithWorkInProgressByDefaultForUser() throws Exception {
    // Make sure owner user is created
    StagedChange sc = stageReviewableChange();
    // All was cleaned already
    assertThat(sender).didNotSend();

    // Toggle workInProgress flag for owner
    GeneralPreferencesInfo prefs = gApi.accounts().id(sc.owner.id().get()).getPreferences();
    prefs.workInProgressByDefault = true;
    gApi.accounts().id(sc.owner.id().get()).setPreferences(prefs);

    // Create another change without notification that should be wip
    StagedPreChange spc = stagePreChange("refs/for/master");
    Truth.assertThat(gApi.changes().id(spc.changeId).get().workInProgress).isTrue();
    assertThat(sender).didNotSend();

    // Clean up workInProgressByDefault by owner
    prefs = gApi.accounts().id(sc.owner.id().get()).getPreferences();
    Truth.assertThat(prefs.workInProgressByDefault).isTrue();
    prefs.workInProgressByDefault = false;
    gApi.accounts().id(sc.owner.id().get()).setPreferences(prefs);
  }

  @Test
  public void createReviewableChangeWithNotifyOwnerReviewers() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER_REVIEWERS");
    assertThat(sender).didNotSend();
  }

  @Test
  public void createReviewableChangeWithNotifyOwner() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).didNotSend();
  }

  @Test
  public void createReviewableChangeWithNotifyNone() throws Exception {
    stagePreChange("refs/for/master%notify=OWNER");
    assertThat(sender).didNotSend();
  }

  @Test
  public void createWipChangeWithNotifyAll() throws Exception {
    StagedPreChange spc = stagePreChange("refs/for/master%wip,notify=ALL");
    assertThat(sender)
        .sent("newchange", spc)
        .to(spc.watchingProjectOwner)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void createReviewableChangeWithReviewersAndCcs() throws Exception {
    StagedPreChange spc =
        stagePreChange(
            "refs/for/master",
            users ->
                ImmutableList.of("r=" + users.reviewer.username(), "cc=" + users.ccer.username()));
    FakeEmailSenderSubject subject =
        assertThat(sender).sent("newchange", spc).to(spc.reviewer, spc.watchingProjectOwner);
    subject.cc(spc.ccer);
    subject.bcc(NEW_CHANGES, NEW_PATCHSETS).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void createReviewableChangeWithReviewersAndCcsByEmail() throws Exception {
    StagedPreChange spc =
        stagePreChange(
            "refs/for/master",
            users -> ImmutableList.of("r=nobody1@example.com,cc=nobody2@example.com"));
    spc.supportReviewersByEmail = true;
    assertThat(sender)
        .sent("newchange", spc)
        .to("nobody1@example.com")
        .to(spc.watchingProjectOwner)
        .cc("nobody2@example.com")
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  /*
   * DeleteReviewerSender tests.
   */

  @Test
  public void deleteReviewerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    requestScopeOperations.setApiUser(sc.owner.id());
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
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
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    requestScopeOperations.setApiUser(admin.id());
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    requestScopeOperations.setApiUser(admin.id());
    removeReviewer(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(sc.owner, extraReviewer)
        .cc(admin, extraCcer, sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteCcerFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    requestScopeOperations.setApiUser(sc.owner.id());
    removeReviewer(sc, extraCcer);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraCcer)
        .cc(sc.reviewer, sc.ccer, extraReviewer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    requestScopeOperations.setApiUser(sc.owner.id());
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteReviewer", sc).to(sc.owner, extraReviewer).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableChangeByOwnerCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    setEmailStrategy(sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    removeReviewer(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    removeReviewer(sc, extraReviewer);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerFromWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    requestScopeOperations.setApiUser(sc.owner.id());
    removeReviewer(sc, extraReviewer, NotifyHandling.ALL);
    assertThat(sender)
        .sent("deleteReviewer", sc)
        .to(extraReviewer)
        .cc(extraCcer, sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    removeReviewer(sc, extraReviewer);
    assertThat(sender).sent("deleteReviewer", sc).to(extraReviewer).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerWithApprovalFromWipChangeNotifyOwner() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    removeReviewer(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteReviewerByEmailFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    gApi.changes().id(sc.changeId).reviewer(StagedUsers.REVIEWER_BY_EMAIL).remove();
    assertThat(sender).didNotSend();
  }

  private void recommend(StagedChange sc, TestAccount by) throws Exception {
    requestScopeOperations.setApiUser(by.id());
    gApi.changes().id(sc.changeId).current().review(ReviewInput.recommend());
  }

  private interface Stager {
    StagedChange stage() throws Exception;
  }

  private StagedChange stageChangeWithExtraReviewer(Stager stager) throws Exception {
    StagedChange sc = stager.stage();
    ReviewInput in =
        ReviewInput.noScore()
            .reviewer(extraReviewer.email())
            .reviewer(extraCcer.email(), ReviewerState.CC, false);
    requestScopeOperations.setApiUser(extraReviewer.id());
    gApi.changes().id(sc.changeId).current().review(in);
    sender.clear();
    return sc;
  }

  private StagedChange stageReviewableChangeWithExtraReviewer() throws Exception {
    StagedChange sc = stageChangeWithExtraReviewer(this::stageReviewableChange);
    sender.clear();
    return sc;
  }

  private StagedChange stageReviewableWipChangeWithExtraReviewer() throws Exception {
    return stageChangeWithExtraReviewer(this::stageReviewableWipChange);
  }

  private StagedChange stageWipChangeWithExtraReviewer() throws Exception {
    StagedChange sc = stageChangeWithExtraReviewer(this::stageWipChange);
    assertThat(sender).didNotSend();
    return sc;
  }

  private void removeReviewer(StagedChange sc, TestAccount account) throws Exception {
    sender.clear();
    gApi.changes().id(sc.changeId).reviewer(account.email()).remove();
  }

  private void removeReviewer(StagedChange sc, TestAccount account, NotifyHandling notify)
      throws Exception {
    sender.clear();
    DeleteReviewerInput in = new DeleteReviewerInput();
    in.notify = notify;
    gApi.changes().id(sc.changeId).reviewer(account.email()).remove(in);
  }

  /*
   * DeleteVoteSender tests.
   */

  @Test
  public void deleteVoteFromReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(admin.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(admin, EmailStrategy.CC_ON_OWN_COMMENTS);
    requestScopeOperations.setApiUser(admin.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwnerReviewersWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER_REVIEWERS);
    assertThat(sender)
        .sent("deleteVote", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(admin.id());
    deleteVote(sc, extraReviewer, NotifyHandling.OWNER);
    assertThat(sender).sent("deleteVote", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableChangeNotifyNoneWithSelfCc() throws Exception {
    StagedChange sc = stageReviewableChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer, NotifyHandling.NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void deleteVoteFromWipChange() throws Exception {
    StagedChange sc = stageWipChangeWithExtraReviewer();
    recommend(sc, extraReviewer);
    requestScopeOperations.setApiUser(sc.owner.id());
    deleteVote(sc, extraReviewer);
    assertThat(sender)
        .sent("deleteVote", sc)
        .cc(sc.reviewer, sc.ccer, extraReviewer, extraCcer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private void deleteVote(StagedChange sc, TestAccount account) throws Exception {
    sender.clear();
    gApi.changes().id(sc.changeId).reviewer(account.email()).deleteVote("Code-Review");
  }

  private void deleteVote(StagedChange sc, TestAccount account, NotifyHandling notify)
      throws Exception {
    sender.clear();
    DeleteVoteInput in = new DeleteVoteInput();
    in.label = "Code-Review";
    in.notify = notify;
    gApi.changes().id(sc.changeId).reviewer(account.email()).deleteVote(in);
  }

  /*
   * MergedSender tests.
   */

  @Test
  public void mergeByOwnerAllSubmitStrategies() throws Exception {
    mergeByOwnerAllSubmitStrategies(false);
  }

  @Test
  public void mergeByOwnerAllSubmitStrategiesWithAdvancingBranch() throws Exception {
    mergeByOwnerAllSubmitStrategies(true);
  }

  private void mergeByOwnerAllSubmitStrategies(boolean advanceBranchBeforeSubmitting)
      throws Exception {
    for (SubmitType submitType : SubmitType.values()) {
      try (ProjectConfigUpdate u = updateProject(project)) {
        u.getConfig().updateProject(p -> p.setSubmitType(submitType));
        u.save();
      }

      StagedChange sc = stageChangeReadyForMerge();

      String name = submitType + " sender";
      if (advanceBranchBeforeSubmitting) {
        if (submitType == SubmitType.FAST_FORWARD_ONLY) {
          continue;
        }
        try (Repository repo = repoManager.openRepository(project);
            TestRepository<Repository> tr = new TestRepository<>(repo)) {
          tr.branch("master").commit().create();
        }
        name += " after branch has advanced";
      }

      merge(sc.changeId, sc.owner);
      assertWithMessage(name)
          .about(fakeEmailSenders())
          .that(sender)
          .sent("merged", sc)
          .cc(sc.reviewer, sc.ccer)
          .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
          .bcc(sc.starrer)
          .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
          .noOneElse();
      assertWithMessage(name).about(fakeEmailSenders()).that(sender).didNotSend();
    }
  }

  @Test
  public void mergeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.owner, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByReviewer() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.reviewer);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByReviewerCcingSelf() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, sc.reviewer, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS, SUBMITTED_CHANGES)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByOtherNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("merged", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByOtherNotifyOwner() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, OWNER);
    assertThat(sender).sent("merged", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByOtherCcingSelfNotifyOwner() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, OWNER);
    assertThat(sender).sent("merged", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByOtherNotifyNone() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    merge(sc.changeId, other, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void mergeByOtherCcingSelfNotifyNone() throws Exception {
    StagedChange sc = stageChangeReadyForMerge();
    setEmailStrategy(other, EmailStrategy.CC_ON_OWN_COMMENTS);
    merge(sc.changeId, other, NONE);
    assertThat(sender).didNotSend();
  }

  private void merge(String changeId, TestAccount by) throws Exception {
    merge(changeId, by, ENABLED);
  }

  private void merge(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    requestScopeOperations.setApiUser(by.id());
    gApi.changes().id(changeId).current().submit();
  }

  private void merge(String changeId, TestAccount by, NotifyHandling notify) throws Exception {
    merge(changeId, by, ENABLED, notify);
  }

  private void merge(
      String changeId, TestAccount by, EmailStrategy emailStrategy, NotifyHandling notify)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    requestScopeOperations.setApiUser(by.id());
    SubmitInput in = new SubmitInput();
    in.notify = notify;
    gApi.changes().id(changeId).current().submit(in);
  }

  private StagedChange stageChangeReadyForMerge() throws Exception {
    StagedChange sc = stageReviewableChange();
    requestScopeOperations.setApiUser(sc.reviewer.id());
    gApi.changes().id(sc.changeId).current().review(ReviewInput.approve());
    sender.clear();
    return sc;
  }

  /*
   * ReplacePatchSetSender tests.
   */

  @Test
  public void newPatchSetByOwnerOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This shouldn't be sent *from* the owner.
        .to(sc.reviewer, other)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This shouldn't be sent *from* the owner.
        .to(sc.reviewer, other)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This shouldn't be sent *from* the owner.
        .to(sc.reviewer)
        .to(other)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewers()
      throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER_REVIEWERS", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .notTo(sc.owner) // TODO(logan): This shouldn't be sent *from* the owner.
        .to(sc.reviewer)
        .to(other)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER", other);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=OWNER", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=NONE", other);
    // TODO(logan): This email shouldn't come from the owner, and that's why
    // no email is currently sent (owner isn't CCing self).
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOtherOnReviewableChangeOwnerSelfCcNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%notify=NONE", other, EmailStrategy.CC_ON_OWN_COMMENTS);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetByOwnerOnReviewableChangeToWip() throws Exception {
    StagedChange sc = stageReviewableChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%wip,notify=ALL", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChangeToReady() throws Exception {
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    pushTo(sc, "refs/for/master%wip", sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnReviewableChangeAddingReviewer() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username(), sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewer() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%r=" + newReviewer.username(), sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChangeAddingReviewerNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    TestAccount newReviewer = sc.testAccount("newReviewer");
    pushTo(sc, "refs/for/master%notify=ALL,r=" + newReviewer.username(), sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer, newReviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void newPatchSetOnWipChangeSettingReady() throws Exception {
    StagedChange sc = stageWipChange();
    pushTo(sc, "refs/for/master%ready", sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by) throws Exception {
    pushTo(sc, ref, by, ENABLED);
  }

  private void pushTo(StagedChange sc, String ref, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    pushFactory.create(by.newIdent(), sc.repo, sc.changeId).to(ref).assertOkStatus();
  }

  @Test
  public void editCommitMessageEditByOwnerOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, sc.owner);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageEditByOtherOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeOwnerSelfCc() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer, other)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeNotifyOwnerReviewers() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, OWNER_REVIEWERS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeOwnerSelfCcNotifyOwnerReviewers()
      throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, OWNER_REVIEWERS, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.owner, sc.reviewer)
        .cc(sc.ccer, other)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, OWNER);
    assertThat(sender).sent("newpatchset", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeOwnerSelfCcNotifyOwner() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, OWNER, CC_ON_OWN_COMMENTS);
    assertThat(sender).sent("newpatchset", sc).to(sc.owner).cc(other).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, NONE);
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnReviewableChangeOwnerSelfCcNotifyNone() throws Exception {
    StagedChange sc = stageReviewableChange();
    editCommitMessage(sc, other, NONE, CC_ON_OWN_COMMENTS);
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    editCommitMessage(sc, sc.owner);
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    editCommitMessage(sc, other);
    assertThat(sender).sent("newpatchset", sc).to(sc.owner).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageByOtherOnWipChangeSelfCc() throws Exception {
    StagedChange sc = stageWipChange();
    editCommitMessage(sc, other, CC_ON_OWN_COMMENTS);
    assertThat(sender).sent("newpatchset", sc).to(sc.owner).cc(other).noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void editCommitMessageOnWipChangeNotifyAll() throws Exception {
    StagedChange sc = stageWipChange();
    editCommitMessage(sc, sc.owner, ALL);
    assertThat(sender)
        .sent("newpatchset", sc)
        .to(sc.reviewer)
        .cc(sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(NEW_PATCHSETS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private void editCommitMessage(StagedChange sc, TestAccount by) throws Exception {
    editCommitMessage(sc, by, null, ENABLED);
  }

  private void editCommitMessage(StagedChange sc, TestAccount by, @Nullable NotifyHandling notify)
      throws Exception {
    editCommitMessage(sc, by, notify, ENABLED);
  }

  private void editCommitMessage(StagedChange sc, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    editCommitMessage(sc, by, null, emailStrategy);
  }

  private void editCommitMessage(
      StagedChange sc, TestAccount by, @Nullable NotifyHandling notify, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    CommitInfo commit = gApi.changes().id(sc.changeId).current().commit(false);
    CommitMessageInput in = new CommitMessageInput();
    in.message = "update\n" + commit.message;
    in.notify = notify;
    gApi.changes().id(sc.changeId).setMessage(in);
  }

  /*
   * RestoredSender tests.
   */

  @Test
  public void restoreReviewableChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void restoreReviewableWipChange() throws Exception {
    StagedChange sc = stageAbandonedReviewableWipChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void restoreWipChange() throws Exception {
    StagedChange sc = stageAbandonedWipChange();
    restore(sc.changeId, sc.owner);
    assertThat(sender)
        .sent("restore", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void restoreReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, admin);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void restoreReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, sc.owner, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void restoreReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageAbandonedReviewableChange();
    restore(sc.changeId, admin, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("restore", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private void restore(String changeId, TestAccount by) throws Exception {
    restore(changeId, by, ENABLED);
  }

  private void restore(String changeId, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    requestScopeOperations.setApiUser(by.id());
    gApi.changes().id(changeId).restore();
  }

  /*
   * RevertedSender tests.
   */

  @Test
  public void revertChangeByOwner() throws Exception {
    StagedChange sc = stageChange();
    revert(sc, sc.owner);

    // email for the newly created revert change
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();

    // email for the change that is reverted
    assertThat(sender)
        .sent("revert", sc)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void revertChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageChange();
    revert(sc, sc.owner, CC_ON_OWN_COMMENTS);

    // email for the newly created revert change
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.owner, sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();

    // email for the change that is reverted
    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void revertChangeByOther() throws Exception {
    StagedChange sc = stageChange();
    revert(sc, other);

    // email for the newly created revert change
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();

    // email for the change that is reverted
    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer, admin)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void revertChangeByOtherCcingSelf() throws Exception {
    StagedChange sc = stageChange();
    revert(sc, other, CC_ON_OWN_COMMENTS);

    // email for the newly created revert change
    assertThat(sender)
        .sent("newchange", sc)
        .to(sc.owner, sc.reviewer, sc.watchingProjectOwner, admin)
        .cc(sc.ccer, other)
        .bcc(NEW_CHANGES, NEW_PATCHSETS)
        .noOneElse();

    // email for the change that is reverted
    assertThat(sender)
        .sent("revert", sc)
        .to(sc.owner)
        .cc(other, sc.reviewer, sc.ccer, admin)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private StagedChange stageChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(sc.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(sc.changeId).current().submit();
    sender.clear();
    return sc;
  }

  private void revert(StagedChange sc, TestAccount by) throws Exception {
    revert(sc, by, ENABLED);
  }

  private void revert(StagedChange sc, TestAccount by, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    requestScopeOperations.setApiUser(by.id());
    gApi.changes().id(sc.changeId).revert();
  }

  /*
   * SetAssigneeSender tests.
   */

  @Test
  public void setAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeOnReviewableChangeByOwnerCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(sc.owner)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdmin() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeOnReviewableChangeByAdminCcingSelf() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, admin, sc.assignee, CC_ON_OWN_COMMENTS);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(admin)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeToSelfOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void changeAssigneeOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    TestAccount other = accountCreator.create("other", "other@example.com", "other", null);
    assign(sc, sc.owner, other);
    sender.clear();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void changeAssigneeToSelfOnReviewableChange() throws Exception {
    StagedChange sc = stageReviewableChange();
    assign(sc, sc.owner, sc.assignee);
    sender.clear();
    assign(sc, sc.owner, sc.owner);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeOnReviewableWipChange() throws Exception {
    StagedChange sc = stageReviewableWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setAssigneeOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    assign(sc, sc.owner, sc.assignee);
    assertThat(sender)
        .sent("setassignee", sc)
        .cc(
            StagedUsers.REVIEWER_BY_EMAIL,
            StagedUsers.CC_BY_EMAIL) // TODO(logan): This is probably not intended!
        .to(sc.assignee)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to) throws Exception {
    assign(sc, by, to, ENABLED);
  }

  private void assign(StagedChange sc, TestAccount by, TestAccount to, EmailStrategy emailStrategy)
      throws Exception {
    setEmailStrategy(by, emailStrategy);
    requestScopeOperations.setApiUser(by.id());
    AssigneeInput in = new AssigneeInput();
    in.assignee = to.email();
    gApi.changes().id(sc.changeId).setAssignee(in);
  }

  /*
   * Start review and WIP tests.
   */

  @Test
  public void startReviewOnWipChange() throws Exception {
    StagedChange sc = stageWipChange();
    startReview(sc);
    assertThat(sender)
        .sent("comment", sc)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void startReviewOnWipChangeCcingSelf() throws Exception {
    StagedChange sc = stageWipChange();
    setEmailStrategy(sc.owner, CC_ON_OWN_COMMENTS);
    startReview(sc);
    assertThat(sender)
        .sent("comment", sc)
        .to(sc.owner)
        .cc(sc.reviewer, sc.ccer)
        .cc(StagedUsers.REVIEWER_BY_EMAIL, StagedUsers.CC_BY_EMAIL)
        .bcc(sc.starrer)
        .bcc(ALL_COMMENTS)
        .noOneElse();
    assertThat(sender).didNotSend();
  }

  @Test
  public void setWorkInProgress() throws Exception {
    StagedChange sc = stageReviewableChange();
    gApi.changes().id(sc.changeId).setWorkInProgress();
    assertThat(sender).didNotSend();
  }

  private void startReview(StagedChange sc) throws Exception {
    requestScopeOperations.setApiUser(sc.owner.id());
    gApi.changes().id(sc.changeId).setReadyForReview();
  }

  private void setWorkInProgressByDefault(Project.NameKey p, InheritableBoolean v)
      throws Exception {
    ConfigInput input = new ConfigInput();
    input.workInProgressByDefault = v;
    gApi.projects().name(p.get()).config(input);
  }
}
