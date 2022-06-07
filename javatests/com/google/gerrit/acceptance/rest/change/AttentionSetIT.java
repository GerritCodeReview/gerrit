// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.restapi.testing.AttentionSetUpdateSubject.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.VerifyNoPiiInChangeNotes;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.AttentionSetInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.GetAttentionSet;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.TestCommentHelper;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseClockStep(clockStepUnit = TimeUnit.MINUTES)
@VerifyNoPiiInChangeNotes(true)
public class AttentionSetIT extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject private TestCommentHelper testCommentHelper;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;
  @Inject private ProjectOperations projectOperations;
  @Inject private GetAttentionSet getAttentionSet;

  /** Simulates a fake clock. Uses second granularity. */
  private static class FakeClock implements LongSupplier {
    Instant now = Instant.now();

    @Override
    public long getAsLong() {
      return TimeUnit.SECONDS.toMillis(now.getEpochSecond());
    }

    Instant now() {
      return Instant.ofEpochSecond(now.getEpochSecond());
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }
  }

  private FakeClock fakeClock = new FakeClock();

  @Before
  public void setUp() {
    TimeUtil.setCurrentMillisSupplier(fakeClock);
  }

  @Test
  public void emptyAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void addUser() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    int accountId =
        change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "first"))._accountId;
    assertThat(accountId).isEqualTo(admin.id().get());
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.ADD, "first");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Second add is ignored.
    accountId =
        change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "second"))._accountId;
    assertThat(accountId).isEqualTo(admin.id().get());
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Only one email since the second add was ignored.
    String emailBody = Iterables.getOnlyElement(sender.getMessages()).body();
    assertThat(emailBody)
        .contains(
            String.format(
                "%s requires the attention of %s to this change.\n The reason is: first.",
                user.fullName(), admin.fullName()));
  }

  @Test
  public void addUserWithTemplateReason() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    String manualReason = "Added by " + AccountTemplateUtil.getAccountTemplate(user.id());
    int accountId =
        change(r).addToAttentionSet(new AttentionSetInput(admin.email(), manualReason))._accountId;
    assertThat(accountId).isEqualTo(admin.id().get());
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.ADD, manualReason);
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);
    AttentionSetInfo attentionSetInfo =
        Iterables.getOnlyElement(change(r).get().attentionSet.values());
    assertThat(attentionSetInfo.reason).isEqualTo(manualReason);
    assertThat(attentionSetInfo.reasonAccount).isEqualTo(getAccountInfo(user.id()));
    assertThat(attentionSetInfo.account).isEqualTo(getAccountInfo(admin.id()));

    AttentionSetInfo getAttentionSetInfo =
        Iterables.getOnlyElement(
            getAttentionSet.apply(parseChangeResource(r.getChangeId())).value());
    assertThat(getAttentionSetInfo.reason).isEqualTo(manualReason);
    assertThat(getAttentionSetInfo.reasonAccount).isEqualTo(getAccountInfo(user.id()));
    assertThat(getAttentionSetInfo.account).isEqualTo(getAccountInfo(admin.id()));
  }

  @Test
  public void addUserWithTemplateReasonMultipleAccounts() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    String manualReason =
        String.format(
            "Added by %s with user %s",
            AccountTemplateUtil.getAccountTemplate(user.id()),
            AccountTemplateUtil.getAccountTemplate(admin.id()));
    int accountId =
        change(r).addToAttentionSet(new AttentionSetInput(admin.email(), manualReason))._accountId;
    assertThat(accountId).isEqualTo(admin.id().get());
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.ADD, manualReason);
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);
    AttentionSetInfo attentionSetInfo =
        Iterables.getOnlyElement(change(r).get().attentionSet.values());
    assertThat(attentionSetInfo.reason).isEqualTo(manualReason);
    assertThat(attentionSetInfo.reasonAccount).isNull();
    assertThat(attentionSetInfo.account).isEqualTo(getAccountInfo(admin.id()));

    AttentionSetInfo getAttentionSetInfo =
        Iterables.getOnlyElement(
            getAttentionSet.apply(parseChangeResource(r.getChangeId())).value());
    assertThat(getAttentionSetInfo.reason).isEqualTo(manualReason);
    assertThat(getAttentionSetInfo.reasonAccount).isNull();
    assertThat(getAttentionSetInfo.account).isEqualTo(getAccountInfo(admin.id()));
  }

  @Test
  public void reviewWithManuallyAddedUserAndTemplateReason() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    String manualReason = "Review by " + AccountTemplateUtil.getAccountTemplate(user.id());
    ReviewInput reviewInput =
        ReviewInput.create().addUserToAttentionSet(user.email(), manualReason);

    change(r).current().review(reviewInput);
    AttentionSetInfo attentionSetInfo = change(r).get().attentionSet.get(user.id().get());
    assertThat(attentionSetInfo.reason).isEqualTo(manualReason);
    assertThat(attentionSetInfo.reasonAccount).isEqualTo(getAccountInfo(user.id()));
    assertThat(attentionSetInfo.account).isEqualTo(getAccountInfo(user.id()));
  }

  @Test
  public void addMultipleUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    Instant timestamp1 = fakeClock.now();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());
    fakeClock.advance(Duration.ofSeconds(42));
    Instant timestamp2 = fakeClock.now();
    int accountId2 =
        change(r)
            .addToAttentionSet(new AttentionSetInput(admin.id().toString(), "manual update"))
            ._accountId;
    assertThat(accountId2).isEqualTo(admin.id().get());

    AttentionSetUpdate expectedAttentionSetUpdate1 =
        AttentionSetUpdate.createFromRead(
            timestamp1, user.id(), AttentionSetUpdate.Operation.ADD, "Reviewer was added");
    AttentionSetUpdate expectedAttentionSetUpdate2 =
        AttentionSetUpdate.createFromRead(
            timestamp2, admin.id(), AttentionSetUpdate.Operation.ADD, "manual update");
    assertThat(r.getChange().attentionSet())
        .containsExactly(expectedAttentionSetUpdate1, expectedAttentionSetUpdate2);
  }

  @Test
  public void removeUser() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());
    sender.clear();
    requestScopeOperations.setApiUser(user.id());

    fakeClock.advance(Duration.ofSeconds(42));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // The removal also shows up in AttentionSetInfo.
    AttentionSetInfo attentionSetInfo =
        Iterables.getOnlyElement(change(r).get().removedFromAttentionSet.values());
    assertThat(attentionSetInfo.reason).isEqualTo("removed");
    assertThat(attentionSetInfo.account).isEqualTo(getAccountInfo(user.id()));

    // Second removal is ignored.
    fakeClock.advance(Duration.ofSeconds(42));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed again"));
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Only one email since the second remove was ignored.
    String emailBody = Iterables.getOnlyElement(sender.getMessages()).body();
    assertThat(emailBody)
        .contains(
            user.fullName()
                + " removed themselves from the attention set of this change.\n"
                + " The reason is: removed.");
  }

  @Test
  public void removeUserWithInvalidUserInput() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                change(r)
                    .attention(user.id().toString())
                    .remove(new AttentionSetInput("invalid user", "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            "invalid user doesn't exist or is not active on the change as an owner, "
                + "uploader, reviewer, or cc so they can't be added to the attention set");

    exception =
        assertThrows(
            BadRequestException.class,
            () ->
                change(r)
                    .attention(user.id().toString())
                    .remove(new AttentionSetInput(admin.email(), "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            "The field \"user\" must be empty, or must match the user specified in the URL.");
  }

  @Test
  public void abandonRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "admin"));

    change(r).abandon();

    AttentionSetUpdate userUpdate =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(userUpdate).hasAccountIdThat().isEqualTo(user.id());
    assertThat(userUpdate).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(userUpdate).hasReasonThat().isEqualTo("Change was abandoned");

    AttentionSetUpdate adminUpdate =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(adminUpdate).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(adminUpdate).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(adminUpdate).hasReasonThat().isEqualTo("Change was abandoned");
  }

  @Test
  public void workInProgressRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    change(r).setWorkInProgress();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was marked work in progress");
  }

  @Test
  public void submitRemovesUsersForAllSubmittedChanges() throws Exception {
    PushOneCommit.Result r1 = createChange("refs/heads/master", "file1", "content");

    // implictly adds the user to the attention set when adding as reviewer
    change(r1).current().review(ReviewInput.approve().reviewer(user.email()));
    PushOneCommit.Result r2 = createChange("refs/heads/master", "file2", "content");
    change(r2).current().review(ReviewInput.approve().reviewer(user.email()));

    change(r2).current().submit();

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r1, user));

    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was submitted");

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r2, user));

    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was submitted");
  }

  @Test
  public void robotSubmitsRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange("refs/heads/master", "file1", "content");

    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    TestAccount robot =
        accountCreator.create(
            "robot2",
            "robot2@example.com",
            "Ro Bot",
            "Ro",
            ServiceUserClassifier.SERVICE_USERS,
            "Administrators");
    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(ReviewInput.approve());
    change(r).current().submit();

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));

    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was submitted");
  }

  @Test
  public void addedReviewersAreAddedToAttentionSetOnMergedChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).current().review(ReviewInput.approve());
    change(r).current().submit();

    change(r).addReviewer(user.email());
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));

    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");
  }

  @Test
  public void reviewersAddedAndRemovedFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).addReviewer(user.id().toString());

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");

    change(r).reviewer(user.email()).remove();

    attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void removedCcRemovedFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add cc
    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.CC;
    change(r).addReviewer(input);

    // Add them to the attention set
    AttentionSetInput attentionSetInput = new AttentionSetInput();
    attentionSetInput.user = user.email();
    attentionSetInput.reason = "reason";
    change(r).addToAttentionSet(attentionSetInput);

    // Remove them from cc
    change(r).reviewer(user.email()).remove();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void reviewersAddedAndRemovedByEmailFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).addReviewer(user.email());

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");

    change(r).reviewer(user.email()).remove();

    attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void reviewersInWorkProgressNotAddedToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void addingReviewerWhileMarkingWorkInProgressDoesntAddToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = ReviewInput.create().setWorkInProgress(true);
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.state = ReviewerState.REVIEWER;
    reviewerInput.reviewer = user.email();
    reviewInput.reviewers = ImmutableList.of(reviewerInput);

    change(r).current().review(reviewInput);
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void reviewersAddedAsReviewersAgainAreNotAddedToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).addReviewer(user.id().toString());
    change(r)
        .attention(user.id().toString())
        .remove(new AttentionSetInput("removed and not re-added when re-adding as reviewer"));

    change(r).addReviewer(user.id().toString());

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet)
        .hasReasonThat()
        .isEqualTo("removed and not re-added when re-adding as reviewer");
  }

  @Test
  public void ccsAreIgnored() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.state = ReviewerState.CC;
    reviewerInput.reviewer = user.email();

    change(r).addReviewer(reviewerInput);

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void ccsConsideredSameAsRemovedForExistingReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addReviewer(user.email());

    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.state = ReviewerState.CC;
    reviewerInput.reviewer = user.email();
    change(r).addReviewer(reviewerInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void robotReadyForReviewAddsAllReviewersToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    TestAccount robot =
        accountCreator.create(
            "robot1",
            "robot1@example.com",
            "Ro Bot",
            "Ro",
            ServiceUserClassifier.SERVICE_USERS,
            "Administrators");
    requestScopeOperations.setApiUser(robot.id());
    change(r).setReadyForReview();
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was marked ready for review");
  }

  @Test
  public void readyForReviewAddsAllReviewersToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    change(r).setReadyForReview();
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was marked ready for review");
  }

  @Test
  public void readyForReviewHasNoEffectOnReadyChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r)
        .current()
        .review(ReviewInput.create().reviewer(user.email()).blockAutomaticAttentionSetRules());

    change(r).current().review(ReviewInput.create().setWorkInProgress(false));
    assertThat(r.getChange().attentionSet()).isEmpty();

    change(r).current().review(ReviewInput.create().setReady(true));
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void workInProgressHasNoEffectOnWorkInProgressChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(user.email())
                .setWorkInProgress(true)
                .addUserToAttentionSet(user.email(), /* reason= */ "reason"));

    change(r).current().review(ReviewInput.create().setWorkInProgress(true));
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");

    change(r).current().review(ReviewInput.create().setReady(false));
    attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void rebaseDoesNotAddToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    // create an unrelated change so that we can rebase
    testRepo.reset("HEAD~1");
    PushOneCommit.Result unrelated = createChange();
    gApi.changes().id(unrelated.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(unrelated.getChangeId()).current().submit();

    gApi.changes().id(r.getChangeId()).rebase();

    // rebase has no impact on the attention set
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void readyForReviewWhileRemovingReviewerRemovesThemToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    ReviewInput reviewInput = ReviewInput.create().setReady(true);
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.state = ReviewerState.CC;
    reviewerInput.reviewer = user.email();
    reviewInput.reviewers = ImmutableList.of(reviewerInput);
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void readyForReviewWhileAddingReviewerAddsThemToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();

    ReviewInput reviewInput = ReviewInput.create().setReady(true).reviewer(user.email());
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");
  }

  @Test
  public void reviewersAreNotAddedForNoReasonBecauseOfAnUpdate() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));

    HashtagsInput hashtagsInput = new HashtagsInput();
    hashtagsInput.add = ImmutableSet.of("tag");
    change(r).setHashtags(hashtagsInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("removed");
  }

  @Test
  public void reviewAddsManuallyAddedUserToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet(user.email(), "reason");

    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");

    // No emails for adding to attention set were sent.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void reviewRemovesManuallyRemovedUserFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());
    requestScopeOperations.setApiUser(user.id());
    sender.clear();

    ReviewInput reviewInput =
        ReviewInput.create().removeUserFromAttentionSet(user.email(), "reason");
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");

    // No emails for removing from attention set were sent.
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void reviewWithManualAdditionToAttentionSetFailsWithoutReason() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet(user.email(), "");

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> change(r).current().review(reviewInput));

    assertThat(exception.getMessage()).isEqualTo("missing field: reason");
  }

  @Test
  public void reviewWithManualAdditionToAttentionSetFailsWithoutUser() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet("", "reason");

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> change(r).current().review(reviewInput));

    assertThat(exception.getMessage()).isEqualTo("missing field: user");
  }

  @Test
  public void reviewAddReviewerWhileRemovingFromAttentionSetJustRemovesUser() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    ReviewInput reviewInput =
        ReviewInput.create()
            .reviewer(user.email())
            .removeUserFromAttentionSet(user.email(), "reason");

    change(r).current().review(reviewInput);

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void cantAddAndRemoveSameUser() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput =
        ReviewInput.create()
            .removeUserFromAttentionSet(user.email(), "reason")
            .addUserToAttentionSet(user.username(), "reason");

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> change(r).current().review(reviewInput));

    assertThat(exception.getMessage())
        .isEqualTo(
            "user1 can not be added/removed twice, and can not be added and removed at the same"
                + " time");
  }

  @Test
  public void cantRemoveSameUserTwice() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput =
        ReviewInput.create()
            .removeUserFromAttentionSet(user.email(), "reason1")
            .removeUserFromAttentionSet(user.username(), "reason2");

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> change(r).current().review(reviewInput));

    assertThat(exception.getMessage())
        .isEqualTo(
            "user1 can not be added/removed twice, and can not be added and removed at the same"
                + " time");
  }

  @Test
  public void reviewDoesNotAddReviewerWithoutAutomaticRules() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = ReviewInput.recommend().blockAutomaticAttentionSetRules();

    change(r).current().review(reviewInput);
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void reviewDoesNotAddReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = ReviewInput.recommend();

    change(r).current().review(reviewInput);
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void cantAddSameUserTwice() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput =
        ReviewInput.create()
            .addUserToAttentionSet(user.email(), "reason1")
            .addUserToAttentionSet(user.username(), "reason2");

    BadRequestException exception =
        assertThrows(BadRequestException.class, () -> change(r).current().review(reviewInput));

    assertThat(exception.getMessage())
        .isEqualTo(
            "user1 can not be added/removed twice, and can not be added and removed at the same"
                + " time");
  }

  @Test
  public void reviewRemoveFromAttentionSetWhileMarkingReadyForReviewJustRemovesUser()
      throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));

    ReviewInput reviewInput =
        ReviewInput.create().setReady(true).removeUserFromAttentionSet(user.email(), "reason");

    change(r).current().review(reviewInput);

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void reviewAddToAttentionSetWhileMarkingWorkInProgressJustAddsUser() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addReviewer(user.email());

    ReviewInput reviewInput =
        ReviewInput.create().setWorkInProgress(true).addUserToAttentionSet(user.email(), "reason");

    change(r).attention(user.email()).remove(new AttentionSetInput("removal"));
    change(r).current().review(reviewInput);

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void reviewRemovesUserFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("removed on reply");
  }

  @Test
  public void reviewAddUserToAttentionSetWhileReplyingJustAddsUser() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet(admin.email(), "reason");
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void reviewWhileAddingThemselvesAsReviewerStillRemovesThem() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());

    // add the user to the attention set.
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(user.email(), ReviewerState.CC, true)
                .addUserToAttentionSet(user.email(), "reason"));

    // add the user as reviewer but still be removed on reply.
    ReviewInput reviewInput = ReviewInput.create().reviewer(user.email());
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("removed on reply");
  }

  @Test
  public void reviewWhileAddingThemselvesAsReviewerDoesNotAddThem() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput = ReviewInput.create().reviewer(user.email());
    change(r).current().review(reviewInput);

    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void repliesAddsOwner() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Someone else replied on the change");
  }

  @Test
  public void repliesDoNotAddOwnerWhenChangeIsClosed() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).abandon();
    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    assertThat(getAttentionSetUpdatesForUser(r, admin)).isEmpty();
  }

  @Test
  public void repliesDoNotAddOwnerWhenChangeIsWorkInProgress() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    assertThat(getAttentionSetUpdatesForUser(r, admin)).isEmpty();
  }

  @Test
  public void repliesDoNotAddOwnerWhenChangeIsBecomingWorkInProgress() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(accountCreator.admin2().id());

    ReviewInput reviewInput = ReviewInput.create().setWorkInProgress(true);
    change(r).current().review(reviewInput);

    assertThat(getAttentionSetUpdatesForUser(r, admin)).isEmpty();
  }

  @Test
  public void repliesAddOwnerWhenChangeIsBecomingReadyForReview() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    requestScopeOperations.setApiUser(accountCreator.admin2().id());

    ReviewInput reviewInput = ReviewInput.create().setReady(true);
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Someone else replied on the change");
  }

  @Test
  public void repliesAddsOwnerAndUploader() throws Exception {
    // Create change with owner: admin
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);

    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());

    change(r).attention(user.email()).remove(new AttentionSetInput("reason"));
    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    // Uploader added
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Someone else replied on the change");

    // Owner added
    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Someone else replied on the change");
  }

  @Test
  public void reviewIgnoresRobotCommentsForAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    testCommentHelper.addRobotComment(
        r.getChangeId(),
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(Patch.COMMIT_MSG));

    requestScopeOperations.setApiUser(admin.id());
    change(r)
        .current()
        .review(
            reviewInReplyToComment(
                Iterables.getOnlyElement(
                        gApi.changes().id(r.getChangeId()).current().robotCommentsAsList())
                    .id));
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void reviewAddsAllUsersInCommentThread() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    change(r).current().review(reviewWithComment());

    TestAccount user2 = accountCreator.user2();

    requestScopeOperations.setApiUser(user2.id());
    change(r)
        .current()
        .review(
            reviewInReplyToComment(
                Iterables.getOnlyElement(
                        gApi.changes().id(r.getChangeId()).current().commentsAsList())
                    .id));

    change(r).attention(user.email()).remove(new AttentionSetInput("removal"));
    requestScopeOperations.setApiUser(admin.id());
    change(r)
        .current()
        .review(
            reviewInReplyToComment(
                gApi.changes().id(r.getChangeId()).current().commentsAsList().get(1).id));

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet)
        .hasReasonThat()
        .isEqualTo("Someone else replied on a comment you posted");

    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user2));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user2.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet)
        .hasReasonThat()
        .isEqualTo("Someone else replied on a comment you posted");
  }

  @Test
  public void reviewAddsAllUsersInCommentThreadWhenOriginalCommentIsARobotComment()
      throws Exception {
    PushOneCommit.Result result = createChange();
    testCommentHelper.addRobotComment(
        result.getChangeId(),
        TestCommentHelper.createRobotCommentInputWithMandatoryFields(Patch.COMMIT_MSG));

    requestScopeOperations.setApiUser(user.id());
    // Reply to the robot comment.
    change(result)
        .current()
        .review(
            reviewInReplyToComment(
                Iterables.getOnlyElement(
                        gApi.changes().id(result.getChangeId()).current().robotCommentsAsList())
                    .id));

    requestScopeOperations.setApiUser(admin.id());
    // Reply to the human comment. which was a reply to the robot comment.
    change(result)
        .current()
        .review(
            reviewInReplyToComment(
                Iterables.getOnlyElement(
                        gApi.changes().id(result.getChangeId()).current().commentsAsList())
                    .id));

    // The user which replied to the robot comment was added to the attention set.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(result, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet)
        .hasReasonThat()
        .isEqualTo("Someone else replied on a comment you posted");
  }

  @Test
  public void reviewAddsAllUsersInCommentThreadEvenOfDifferentChildBranch() throws Exception {
    Account.Id changeOwner = accountOperations.newAccount().create();
    Change.Id changeId = changeOperations.newChange().owner(changeOwner).create();
    Account.Id user1 = accountOperations.newAccount().create();
    Account.Id user2 = accountOperations.newAccount().create();
    Account.Id user3 = accountOperations.newAccount().create();
    Account.Id user4 = accountOperations.newAccount().create();
    // Add users as reviewers.
    gApi.changes().id(changeId.get()).addReviewer(user1.toString());
    gApi.changes().id(changeId.get()).addReviewer(user2.toString());
    gApi.changes().id(changeId.get()).addReviewer(user3.toString());
    gApi.changes().id(changeId.get()).addReviewer(user4.toString());
    // Add a comment thread with branches. Such threads occur if people reply in parallel without
    // having seen/loaded the reply of another person.
    String root =
        changeOperations.change(changeId).currentPatchset().newComment().author(user1).create();
    String sibling1 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .author(user2)
            .parentUuid(root)
            .create();
    String sibling2 =
        changeOperations
            .change(changeId)
            .currentPatchset()
            .newComment()
            .author(user3)
            .parentUuid(root)
            .create();
    changeOperations
        .change(changeId)
        .currentPatchset()
        .newComment()
        .author(user4)
        .parentUuid(sibling2)
        .create();
    // Clear the attention set. Necessary as we used Gerrit APIs above which affect the attention
    // set.
    AttentionSetInput clearAttention = new AttentionSetInput("clear attention set");
    gApi.changes().id(changeId.get()).attention(user1.toString()).remove(clearAttention);
    gApi.changes().id(changeId.get()).attention(user2.toString()).remove(clearAttention);
    gApi.changes().id(changeId.get()).attention(user3.toString()).remove(clearAttention);
    gApi.changes().id(changeId.get()).attention(user4.toString()).remove(clearAttention);

    requestScopeOperations.setApiUser(changeOwner);
    // Simulate that this reply is a child of sibling1 and thus parallel to sibling2 and its child.
    gApi.changes().id(changeId.get()).current().review(reviewInReplyToComment(sibling1));

    List<AttentionSetUpdate> attentionSetUpdates = getAttentionSetUpdates(changeId);
    assertThat(attentionSetUpdates)
        .comparingElementsUsing(hasAccount())
        .containsExactly(user1, user2, user3, user4);
  }

  @Test
  public void reviewAddsAllUsersInCommentThreadWhenPostedAsDraft() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    change(r).current().review(reviewWithComment());

    requestScopeOperations.setApiUser(admin.id());
    testCommentHelper.addDraft(
        r.getChangeId(),
        testCommentHelper.newDraft(
            "message",
            Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).current().commentsAsList())
                .id));

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.drafts = ReviewInput.DraftHandling.PUBLISH;
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet)
        .hasReasonThat()
        .isEqualTo("Someone else replied on a comment you posted");
  }

  @Test
  public void reviewDoesNotAddUsersInACommentThreadThatAreNotActiveInTheChange() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    change(r).current().review(reviewWithComment());
    change(r).reviewer(user.id().toString()).remove(new DeleteReviewerInput());

    requestScopeOperations.setApiUser(admin.id());
    change(r)
        .current()
        .review(
            reviewInReplyToComment(
                Iterables.getOnlyElement(
                        gApi.changes().id(r.getChangeId()).current().commentsAsList())
                    .id));

    // The user was to be added, but was not added since that user is no longer a
    // reviewer/cc/owner/uploader.
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void ownerRepliesWhileRemovingReviewerRemovesFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addReviewer(user.email());

    ReviewInput reviewInput = ReviewInput.create().reviewer(user.email(), ReviewerState.CC, false);
    change(r).current().review(reviewInput);

    // cc removed
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer/Cc was removed");
  }

  @Test
  public void uploaderRepliesAddsOwner() throws Exception {
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);

    // Add reviewer and cc
    TestAccount reviewer = accountCreator.user2();
    TestAccount cc = accountCreator.admin2();
    ReviewInput reviewInput = new ReviewInput().blockAutomaticAttentionSetRules();
    reviewInput = reviewInput.reviewer(reviewer.email());
    reviewInput.reviewer(cc.email(), ReviewerState.CC, false);
    change(r).current().review(reviewInput);

    requestScopeOperations.setApiUser(user.id());

    change(r).current().review(new ReviewInput());

    // Reviewer and CC not added since the uploader didn't reply to their comments
    assertThat(getAttentionSetUpdatesForUser(r, cc)).isEmpty();
    assertThat(getAttentionSetUpdatesForUser(r, reviewer)).isEmpty();

    // Owner added
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Someone else replied on the change");
  }

  @Test
  public void repliesWhileAddingAsReviewerStillRemovesUser() throws Exception {
    PushOneCommit.Result r = createChange();
    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "remove"));

    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = ReviewInput.recommend();
    change(r).current().review(reviewInput);

    // reviewer removed
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("removed on reply");
  }

  @Test
  public void attentionSetUnchangedWithIgnoreAutomaticAttentionSetRules() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(admin.email(), ReviewerState.CC, false)
                .blockAutomaticAttentionSetRules());

    // admin is still in the attention set, although replies remove from attention set, and removing
    // from reviewer also should remove from attention set.
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void ownerNotAddedAsReviewerToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).current().review(ReviewInput.approve());
    assertThat(getAttentionSetUpdatesForUser(r, admin)).isEmpty();
  }

  @Test
  public void ownerNotAddedAsReviewerToAttentionSetWithoutAutomaticRules() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).current().review(ReviewInput.approve().blockAutomaticAttentionSetRules());
    assertThat(getAttentionSetUpdatesForUser(r, admin)).isEmpty();
  }

  @Test
  public void uploaderNotAddedAsReviewerToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    amendChangeWithUploader(r, project, user);
    requestScopeOperations.setApiUser(user.id());

    change(r).current().review(ReviewInput.recommend());
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void uploaderNotAddedAsReviewerToAttentionSetWithoutAutomaticRules() throws Exception {
    PushOneCommit.Result r = createChange();
    amendChangeWithUploader(r, project, user);
    requestScopeOperations.setApiUser(user.id());

    change(r).current().review(ReviewInput.recommend().blockAutomaticAttentionSetRules());
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void attentionSetStillChangesWithIgnoreAutomaticAttentionSetRulesWithInputList()
      throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .removeUserFromAttentionSet(admin.email(), "removed")
                .blockAutomaticAttentionSetRules());

    // Admin is still removed although we block default attention set rules, since we remove
    // the admin manually.
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("removed");
  }

  @Test
  public void robotsNotAddedToAttentionSet() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot1", "robot1@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    // Make the robot active on the change.
    change(r).addReviewer(robot.email());

    // Throw an error when adding a robot explicitly.
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> change(r).addToAttentionSet(new AttentionSetInput(robot.email(), "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            "robot1@example.com is a robot, and robots can't be added to the attention set.");

    // Robots are not added implicitly.
    change(r).addReviewer(robot.email());
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotAddingAReviewerChangeAttentionSet() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    change(r).addReviewer(user.id().toString());

    // Bots can still change the attention set, just not when replying.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");
  }

  @Test
  public void robotReviewDoesNotChangeAttentionSet() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(ReviewInput.recommend());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotReviewWithNegativeLabelDoesNotAddOwnerOnWorkInProgressChanges()
      throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).setWorkInProgress();

    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(ReviewInput.dislike());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotReviewWithNegativeLabelAddsOwner() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(ReviewInput.dislike());

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("A robot voted negatively on a label");
  }

  @Test
  public void robotCommentDoesNotAddOwnerOnClosedChanges() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).abandon();

    requestScopeOperations.setApiUser(robot.id());
    ReviewInput reviewInput = new ReviewInput();
    ReviewInput.RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields("a.txt");
    reviewInput.robotComments = ImmutableMap.of("a.txt", ImmutableList.of(robotCommentInput));
    change(r).current().review(reviewInput);

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotCanChangeAttentionSetExplicitly() throws Exception {
    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", ServiceUserClassifier.SERVICE_USERS);
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(new ReviewInput().addUserToAttentionSet(admin.email(), "reason"));

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
  }

  @Test
  public void addUsersToAttentionSetInPrivateChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setPrivate(true);

    // implictly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");
  }

  @Test
  public void addUsersAsReviewerAndAttentionSetInPrivateChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setPrivate(true);
    change(r).current().review(new ReviewInput().reviewer(user.email()));

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Reviewer was added");
  }

  @Test
  public void addToAttentionSetEmail_withTemplateReason() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    String templateReason = "Added by " + AccountTemplateUtil.getAccountTemplate(user.id());
    int accountId =
        change(r)
            .addToAttentionSet(new AttentionSetInput(admin.email(), templateReason))
            ._accountId;

    assertThat(accountId).isEqualTo(admin.id().get());
    String emailBody = Iterables.getOnlyElement(sender.getMessages()).body();
    assertThat(emailBody)
        .contains(
            String.format(
                "%s requires the attention of %s to this change.\n The reason is: Added by %s.",
                user.fullName(), admin.fullName(), user.getNameEmail()));
  }

  @Test
  public void removeFromAttentionSetEmail_withTemplateReason() throws Exception {
    PushOneCommit.Result r = createChange();
    // implicitly adds the user to the attention set when adding as reviewer
    change(r).addReviewer(user.email());
    sender.clear();
    requestScopeOperations.setApiUser(user.id());

    String templateReason = "Removed by " + AccountTemplateUtil.getAccountTemplate(user.id());
    change(r).attention(user.id().toString()).remove(new AttentionSetInput(templateReason));

    String emailBody = Iterables.getOnlyElement(sender.getMessages()).body();
    assertThat(emailBody)
        .contains(
            String.format(
                "%s removed themselves from the attention set of this change.\n"
                    + " The reason is: Removed by %s.",
                user.fullName(), user.getNameEmail()));
  }

  @Test
  public void attentionSetEmailFooter() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add user to attention set. They receive an email with the attention footer.
    change(r).addReviewer(user.id().toString());
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .contains("Gerrit-Attention: " + user.fullName());
    sender.clear();

    // Irrelevant reply, User is still in the attention set.
    change(r).current().review(ReviewInput.approve());
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .contains("Gerrit-Attention: " + user.fullName());
    sender.clear();

    // Abandon the change which removes user from attention set; there is an email but without the
    // attention footer.
    change(r).abandon();
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .doesNotContain("Gerrit-Attention: " + user.fullName());
    sender.clear();
  }

  @Test
  @GerritConfig(name = "change.enableAttentionSet", value = "true")
  public void attentionSetEmailHeader() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount user2 = accountCreator.user2();

    // The pattern ensures the header mentions the attention set requirements in any order.
    Pattern attentionSetHeaderPattern =
        Pattern.compile(
            String.format(
                "Attention is currently required from: (%s|%s), (%s|%s).",
                user2.fullName(), user.fullName(), user.fullName(), user2.fullName()));
    // Add user and user2 to the attention set.
    change(r)
        .current()
        .review(
            ReviewInput.create().reviewer(user.email()).reviewer(accountCreator.user2().email()));
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .containsMatch(attentionSetHeaderPattern);
    assertThat(Iterables.getOnlyElement(sender.getMessages()).htmlBody())
        .containsMatch(attentionSetHeaderPattern);
    sender.clear();

    // Irrelevant reply, User and User2 are still in the attention set.
    change(r).current().review(ReviewInput.approve());
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .containsMatch(attentionSetHeaderPattern);
    assertThat(Iterables.getOnlyElement(sender.getMessages()).htmlBody())
        .containsMatch(attentionSetHeaderPattern);
    sender.clear();

    // Abandon the change which removes user from attention set; there is an email but without the
    // attention footer.
    change(r).abandon();
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .doesNotContain("Attention is currently required");
    assertThat(Iterables.getOnlyElement(sender.getMessages()).htmlBody())
        .doesNotContain("Attention is currently required");
    sender.clear();
  }

  @Test
  @GerritConfig(name = "change.enableAttentionSet", value = "false")
  public void noReferenceToAttentionSetInEmailsWhenDisabled() throws Exception {
    PushOneCommit.Result r = createChange();
    // Add user and to the attention set.
    change(r).addReviewer(user.id().toString());

    // Attention set is not referenced.
    assertThat(Iterables.getOnlyElement(sender.getMessages()).body())
        .doesNotContain("Attention is currently required");
    assertThat(Iterables.getOnlyElement(sender.getMessages()).htmlBody())
        .doesNotContain("Attention is currently required");
    sender.clear();
  }

  @Test
  public void attentionSetWithEmailFilter() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    setEmailStrategyForUser(EmailStrategy.ATTENTION_SET_ONLY);

    // Add user to attention set. They receive an email since they are in the attention set.
    change(r).addReviewer(user.id().toString());
    assertThat(sender.getMessages()).isNotEmpty();
    sender.clear();

    // Irrelevant reply, User is still in the attention set, thus got another email.
    change(r).current().review(ReviewInput.approve());
    assertThat(sender.getMessages()).isNotEmpty();
    sender.clear();

    // Abandon the change which removes user from attention set; the user doesn't receive an email
    // since they are not in the attention set.
    change(r).abandon();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void attentionSetWithEmailFilterFiltersNewPatchsets() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    requestScopeOperations.setApiUser(user.id());
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = EmailStrategy.ATTENTION_SET_ONLY;
    gApi.accounts().self().setPreferences(prefs);
    requestScopeOperations.setApiUser(admin.id());

    // Add user to reviewers but not to the attention set
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(user.email())
                .removeUserFromAttentionSet(user.email(), "reason"));
    sender.clear();

    // amending a change doesn't send an email when user is not in the attention set.
    amendChange(r.getChangeId());
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void attentionSetWithEmailFilterStillReceivesSubmitEmail() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    requestScopeOperations.setApiUser(user.id());
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = EmailStrategy.ATTENTION_SET_ONLY;
    gApi.accounts().self().setPreferences(prefs);
    requestScopeOperations.setApiUser(admin.id());

    // Add user to reviewers but not to the attention set
    change(r)
        .current()
        .review(
            ReviewInput.approve()
                .reviewer(user.email())
                .removeUserFromAttentionSet(user.email(), "reason"));
    sender.clear();

    // submitting the change sends an email even when user is not in the attention set.
    change(r).current().submit();
    assertThat(sender.getMessages()).isNotEmpty();
  }

  @Test
  public void attentionSetWithEmailFilterImpactingOnlyChangeEmails() throws Exception {
    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    setEmailStrategyForUser(EmailStrategy.ATTENTION_SET_ONLY);

    // Ensure emails that don't relate to changes are still sent.
    gApi.accounts().id(user.id().get()).generateHttpPassword();
    assertThat(sender.getMessages()).isNotEmpty();
  }

  @Test
  public void cannotAddIrrelevantUserToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "%s doesn't exist or is not active on the change as an owner, uploader, reviewer, "
                    + "or cc so they can't be added to the attention set",
                user.email()));
  }

  @Test
  public void cannotAddNonExistingUserToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> change(r).addToAttentionSet(new AttentionSetInput("INVALID USER", "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            "INVALID USER doesn't exist or is not active on the change as an owner,"
                + " uploader, reviewer, or cc so they can't be added to the attention set");
  }

  @Test
  public void cannotRemoveIrrelevantUserToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> change(r).attention(user.email()).remove(new AttentionSetInput("reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "%s doesn't exist or is not active on the change as an owner, uploader, reviewer, "
                    + "or cc so they can't be added to the attention set",
                user.email()));
  }

  @Test
  public void cannotRemoveIrrelevantUserToAttentionSetWithUserInInput() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                change(r)
                    .attention(user.email())
                    .remove(new AttentionSetInput(user.email(), "reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            String.format(
                "%s doesn't exist or is not active on the change as an owner, uploader, reviewer, "
                    + "or cc so they can't be added to the attention set",
                user.email()));
  }

  @Test
  public void cannotRemoveNonExistingUser() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () -> change(r).attention("INVALID USER").remove(new AttentionSetInput("reason")));
    assertThat(exception.getMessage())
        .isEqualTo(
            "INVALID USER doesn't exist or is not active on the change as an owner,"
                + " uploader, reviewer, or cc so they can't be added to the attention set");
  }

  @Test
  public void irrelevantUsersAddedToAttentionSetAreIgnoredOnReply() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).current().review(ReviewInput.create().addUserToAttentionSet(user.email(), "reason"));
    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void newReviewerCanBeAddedToTheAttentionSetManually() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(user.email())
                .addUserToAttentionSet(user.email(), "reason")
                .blockAutomaticAttentionSetRules());
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user)).operation())
        .isEqualTo(Operation.ADD);
  }

  @Test
  public void newReviewerCanBeAddedToTheAttentionSetAutomatically() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).current().review(ReviewInput.create().reviewer(user.email()));
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user)).operation())
        .isEqualTo(Operation.ADD);
  }

  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void onReplyCanAddInvisibleUsersToAttentionSetOnVisibleChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());

    // admin is invisible to the user, but they can still add them to the attention set since they
    // see the change.
    change(r).current().review(ReviewInput.create().addUserToAttentionSet(admin.email(), "reason"));
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin)).operation())
        .isEqualTo(Operation.ADD);
  }

  @Test
  public void onReplyNonExistingUsersAreSilentlyIgnored() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r)
        .current()
        .review(ReviewInput.create().addUserToAttentionSet("INVALID USER", "reason"));
    assertThat(getAttentionSetUpdates(r.getChange().getId())).isEmpty();
  }

  @Test
  public void usersNotPartOfTheChangeAreNeverInTheAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());

    AttentionSetUpdate attentionSetUpdate =
        Iterables.getOnlyElement(getAttentionSetUpdates(r.getChange().getId()));
    assertThat(attentionSetUpdate).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSetUpdate).hasOperationThat().isEqualTo(Operation.ADD);

    ReviewInput reviewInput = ReviewInput.create();
    reviewInput.reviewer(user.email(), ReviewerState.REMOVED, /* confirmed= */ true);
    reviewInput.ignoreAutomaticAttentionSetRules = true;
    change(r).current().review(reviewInput);

    // user removed from the attention set although we ignored automatic attention set rules.
    attentionSetUpdate = Iterables.getOnlyElement(getAttentionSetUpdates(r.getChange().getId()));
    assertThat(attentionSetUpdate).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSetUpdate).hasOperationThat().isEqualTo(Operation.REMOVE);
    assertThat(attentionSetUpdate)
        .hasReasonThat()
        .isEqualTo("Only change owner, uploader, reviewers, and cc can be in the attention set");
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void canModifyAttentionSetForInvisibleUsersOnVisibleChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());

    // admin is invisible to the user, but they can still add them to the attention set since they
    // see the change.
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin)).operation())
        .isEqualTo(Operation.ADD);

    // admin is invisible to the user, but they can still remove them to the attention set since
    // they see the change.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin)).operation())
        .isEqualTo(Operation.REMOVE);
  }

  @Test
  public void deleteSelfVotesDoesNotAddToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    approve(r.getChangeId());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .reviewer(admin.id().toString())
        .deleteVote(LabelId.CODE_REVIEW);

    assertThat(getAttentionSetUpdates(r.getChange().getId())).isEmpty();
  }

  @Test
  public void deleteVotesDoesNotAffectAttentionSetWhenIgnoreAutomaticRulesIsSet() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());

    requestScopeOperations.setApiUser(admin.id());

    DeleteVoteInput deleteVoteInput = new DeleteVoteInput();
    deleteVoteInput.label = LabelId.CODE_REVIEW;

    // set this to true to not change the attention set.
    deleteVoteInput.ignoreAutomaticAttentionSetRules = true;

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .reviewer(user.id().toString())
        .deleteVote(deleteVoteInput);

    assertThat(getAttentionSetUpdatesForUser(r, user)).isEmpty();
  }

  @Test
  public void deleteVotesOfOthersAddThemToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .reviewer(user.id().toString())
        .deleteVote(LabelId.CODE_REVIEW);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Their vote was deleted");
  }

  @Test
  public void accountsWithNoReadPermissionIgnoredOnReply() throws Exception {
    // Create a group with user.
    GroupInput groupInput = new GroupInput();
    groupInput.name = name("User");
    groupInput.members = ImmutableList.of(String.valueOf(user.id()));
    GroupInfo group = gApi.groups().create(groupInput).get();

    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());

    // remove read permission for user.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/*").group(AccountGroup.uuid(group.id)))
        .update();

    // removing user without permissions from attention set is allowed on reply.
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().removeUserFromAttentionSet(user.email(), "reason"));

    // Add user to attention throws an exception.
    assertThrows(
        AuthException.class,
        () -> change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason")));

    // Add user to attention set is ignored on reply.
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().addUserToAttentionSet(user.email(), "reason"));
    assertThat(Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user)).operation())
        .isEqualTo(Operation.REMOVE);
  }

  @Test
  public void outsideAttentionSet_watchProjectEmailReceived() throws Exception {
    setEmailStrategyForUser(EmailStrategy.ATTENTION_SET_ONLY);

    requestScopeOperations.setApiUser(user.id());
    watch(project.get());

    createChange();

    assertThat(sender.getMessages()).isNotEmpty();
    sender.clear();
  }

  @Test
  public void approverOfOutdatedApprovalAddedToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Add an approval that gets outdated when a new patch set is created (i.e. an approval that is
    // not copied).
    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the admin user to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove admin user from attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Amend the change, this removes the vote from user, as it is not copied to the new patch set.
    sender.clear();
    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();

    // Verify that the approval has been removed.
    assertThat(r.getChange().currentApprovals()).isEmpty();

    // User got added to the attention set because users approval got outdated and was removed and
    // user now needs to re-review the change and renew the approval.
    assertThat(r.getChange().attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Vote got outdated and was removed: Code-Review+1"));

    // Expect that the email notification contains the outdated vote.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s.\n"
                    + "\n"
                    + "Hello %s, \n"
                    + "\n"
                    + "I'd like you to reexamine a change."
                    + " Please visit",
                user.fullName(), user.fullName()));
    assertThat(message.body())
        .contains(
            String.format(
                "The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s\n",
                user.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "<p> Attention is currently required from: %s. </p>\n"
                    + "<p>%s <strong>uploaded patch set #2</strong> to this change.</p>",
                user.fullName(), admin.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "View Change</a></p>"
                    + "<p>The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s</p>",
                user.fullName()));
  }

  @Test
  public void approverOfMultipleOutdatedApprovalsAddedToAttentionSet() throws Exception {
    // Create a Verify and a Foo-Var label and allow voting on it.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());

      LabelType.Builder fooBar =
          labelBuilder("Foo-Bar", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(fooBar.build());

      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .add(
            allowLabel("Foo-Bar")
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Add multiple approvals from one user that gets outdated when a new patch set is created (i.e.
    // approvals that are not copied).
    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.VERIFIED, 1));
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Foo-Bar", -1));
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the admin user to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove admin user from attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Amend the change, this removes the vote from user, as it is not copied to the new patch set.
    sender.clear();
    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();

    // Verify that the approvals have been removed.
    assertThat(r.getChange().currentApprovals()).isEmpty();

    // User got added to the attention set because users approvals got outdated and were removed and
    // user now needs to re-review the change and renew the approvals.
    assertThat(r.getChange().attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Votes got outdated and were removed: Code-Review+1, Foo-Bar-1, Verified+1"));

    // Expect that the email notification contains the outdated votes.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s.\n"
                    + "\n"
                    + "Hello %s, \n"
                    + "\n"
                    + "I'd like you to reexamine a change."
                    + " Please visit",
                user.fullName(), user.fullName()));
    assertThat(message.body())
        .contains(
            String.format(
                "The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s, Foo-Bar-1 by %s, Verified+1 by %s\n",
                user.fullName(), user.fullName(), user.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "<p> Attention is currently required from: %s. </p>\n"
                    + "<p>%s <strong>uploaded patch set #2</strong> to this change.</p>",
                user.fullName(), admin.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "View Change</a></p>"
                    + "<p>The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s, Foo-Bar-1 by %s, Verified+1 by %s</p>",
                user.fullName(), user.fullName(), user.fullName()));
  }

  @Test
  public void multipleApproverOfOutdatedApprovalsAddedToAttentionSet() throws Exception {
    // Create Verify label and allow voting on it.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.VERIFIED)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();

    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Add approvals from multiple users that gets outdated when a new patch set is created (i.e.
    // approvals that are not copied).
    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());
    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.VERIFIED, 1));
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the admin user to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove admin user from attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Amend the change, this removes the vote from user, as it is not copied to the new patch set.
    sender.clear();
    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();

    // Verify that the approvals have been removed.
    assertThat(r.getChange().currentApprovals()).isEmpty();

    // User got added to the attention set because users approvals got outdated and were removed and
    // user now needs to re-review the change and renew the approvals.
    assertThat(r.getChange().attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Vote got outdated and was removed: Code-Review+1"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user2.id(),
                AttentionSetUpdate.Operation.ADD,
                "Vote got outdated and was removed: Verified+1"));

    // Expect that the email notification contains the outdated votes.
    assertThat(sender.getMessages()).hasSize(1);
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "Hello %s, %s, \n"
                    + "\n"
                    + "I'd like you to reexamine a change."
                    + " Please visit",
                user.fullName(), user2.fullName(), user.fullName(), user2.fullName()));
    assertThat(message.body())
        .contains(
            String.format(
                "The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s, Verified+1 by %s\n",
                user.fullName(), user2.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "<p> Attention is currently required from: %s, %s. </p>\n"
                    + "<p>%s <strong>uploaded patch set #2</strong> to this change.</p>",
                user.fullName(), user2.fullName(), admin.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "View Change</a></p>"
                    + "<p>The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s, Verified+1 by %s</p>",
                user.fullName(), user2.fullName()));
  }

  @Test
  public void robotApproverOfOutdatedApprovalIsNotAddedToAttentionSet() throws Exception {
    // Create robot account
    TestAccount robot =
        accountCreator.create(
            "robot-X",
            "robot-x@example.com",
            "Ro Bot X",
            "RoX",
            ServiceUserClassifier.SERVICE_USERS,
            "Administrators");

    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Add an approval by a robot that gets outdated when a new patch set is created (i.e. an
    // approval that is not copied).
    requestScopeOperations.setApiUser(robot.id());
    recommend(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // A robot vote doesn't add the admin user to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet()).isEmpty();

    // Amend the change, this removes the vote from the robot, as it is not copied to the new patch
    // set.
    sender.clear();
    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();

    // Verify that the approval has been removed.
    assertThat(r.getChange().currentApprovals()).isEmpty();

    // The robot was not added to the attention set because users service users are never added to
    // the attention set.
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Verify the email for the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    String emailBody = message.body();
    assertThat(emailBody)
        .doesNotContain(
            String.format("Attention is currently required from: %s", robot.fullName()));
    assertThat(emailBody)
        .contains(
            String.format(
                "Hello %s, \n\nI'd like you to reexamine a change. Please visit",
                robot.fullName()));
    assertThat(emailBody)
        .contains(
            String.format(
                "The following approvals got outdated and were removed:\nCode-Review+1 by %s",
                robot.fullName()));
    assertThat(message.htmlBody())
        .doesNotContain(
            String.format("Attention is currently required from: %s", robot.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "<p>%s <strong>uploaded patch set #2</strong> to this change.</p>",
                admin.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "View Change</a></p>"
                    + "<p>The following approvals got outdated and were removed:\n"
                    + "Code-Review+1 by %s</p>",
                robot.fullName()));
  }

  @Test
  public void approverOfCopiedApprovelNotAddedToAttentionSet() throws Exception {
    // Allow user to make veto votes.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 1))
        .update();

    PushOneCommit.Result r = createChange();
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Add a veto vote that will be copied over to a new patch set.
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, -2));
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the admin user to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove admin user from attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Amend the change, this copies the vote from user to the new patch set.
    sender.clear();
    r = amendChange(r.getChangeId(), "refs/for/master", admin, testRepo);
    r.assertOkStatus();

    // Verify that the approval has been copied.
    List<PatchSetApproval> approvalsPs2 = r.getChange().currentApprovals();
    assertThat(approvalsPs2).hasSize(1);
    assertThat(Iterables.getOnlyElement(approvalsPs2).copied()).isTrue();

    // Attention set wasn't changed.
    assertThat(r.getChange().attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Verify the email for the new patch set.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .doesNotContain(String.format("Attention is currently required from: %s", user.fullName()));
    assertThat(message.body())
        .contains(
            String.format(
                "Hello %s, \n\nI'd like you to reexamine a change. Please visit", user.fullName()));
    assertThat(message.body())
        .doesNotContain("The following approvals got outdated and were removed:");
    assertThat(message.htmlBody())
        .doesNotContain(String.format("Attention is currently required from: %s", user.fullName()));
    assertThat(message.htmlBody())
        .contains(
            String.format(
                "<p>%s <strong>uploaded patch set #2</strong> to this change.</p>",
                admin.fullName()));
    assertThat(message.htmlBody())
        .doesNotContain("The following approvals got outdated and were removed:");
  }

  @Test
  public void ownerAndUploaderAreAddedToAttentionSetWhenChangeBecomesUnsubmittable_approvalRemoved()
      throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Attention set is empty.
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the owner (admin) and the uploader (user) to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove the owner (admin) and the uploader (user) from the attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Revoke the approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.noScore());

    // Removing the approval added the owner (admin) and the uploader (user) to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Verify the email notification that has been sent for removing the approval.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change.",
                admin.fullName(), user.fullName(), approver.fullName()));
    assertThat(message.body()).doesNotContain("\nPatch Set 2: Code-Review+2\n");
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
  }

  @Test
  public void
      ownerAndUploaderAreAddedToAttentionSetWhenChangeBecomesUnsubmittable_approvalDowngraded()
          throws Exception {
    // Allow all users to approve.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Attention set is empty.
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the owner (admin) and the uploader (user) to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove the owner (admin) and the uploader (user) from the attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Downgrade the approval
    requestScopeOperations.setApiUser(approver.id());
    sender.clear();
    recommend(r.getChangeId());

    // Changing the approval added the owner (admin) and the uploader (user) to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Verify the email notification that has been sent for downgrading the approval.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change.",
                admin.fullName(), user.fullName(), approver.fullName()));
    assertThat(message.body()).doesNotContain("\nPatch Set 2: Code-Review+2\n");
    assertThat(message.body()).contains("\nPatch Set 2: Code-Review+1\n");
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
  }

  @Test
  public void ownerAndUploaderAreAddedToAttentionSetWhenChangeBecomesUnsubmittable_vetoApplied()
      throws Exception {
    // Allow all users to approve and veto.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create change with admin as the owner and upload a new patch set with user as uploader.
    PushOneCommit.Result r = createChange();
    r = amendChangeWithUploader(r, project, user);
    r.assertOkStatus();

    // Attention set is empty.
    assertThat(r.getChange().attentionSet()).isEmpty();

    // Approve the change.
    TestAccount approver = accountCreator.user2();
    requestScopeOperations.setApiUser(approver.id());
    approve(r.getChangeId());
    requestScopeOperations.setApiUser(admin.id());

    // Voting added the owner (admin) and the uploader (user) to the attention set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Remove the owner (admin) and the uploader (user) from the attention set.
    change(r).attention(admin.id().toString()).remove(new AttentionSetInput("removed"));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), admin.id(), AttentionSetUpdate.Operation.REMOVE, "removed"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed"));

    // Apply veto by another user.
    TestAccount approver2 = accountCreator.user2();
    sender.clear();
    requestScopeOperations.setApiUser(approver2.id());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());

    // Adding the veto approval added the owner (admin) and the uploader (user) to the attention
    // set.
    assertThat(changeDataFactory.create(project, r.getChange().getId()).attentionSet())
        .containsExactly(
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                admin.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"),
            AttentionSetUpdate.createFromRead(
                fakeClock.now(),
                user.id(),
                AttentionSetUpdate.Operation.ADD,
                "Someone else replied on the change"));

    // Verify the email notification that has been sent for adding the veto.
    Message message = Iterables.getOnlyElement(sender.getMessages());
    assertThat(message.body())
        .contains(
            String.format(
                "Attention is currently required from: %s, %s.\n"
                    + "\n"
                    + "%s has posted comments on this change.",
                admin.fullName(), user.fullName(), approver.fullName()));
    assertThat(message.body()).contains("\nPatch Set 2: Code-Review-2\n");
    assertThat(message.body())
        .contains("The change is no longer submittable: Code-Review is unsatisfied now.\n");
  }

  private void setEmailStrategyForUser(EmailStrategy es) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = es;
    gApi.accounts().self().setPreferences(prefs);
    requestScopeOperations.setApiUser(admin.id());
  }

  private List<AttentionSetUpdate> getAttentionSetUpdatesForUser(
      PushOneCommit.Result r, TestAccount account) {
    return getAttentionSetUpdates(r.getChange().getId()).stream()
        .filter(a -> a.account().equals(account.id()))
        .collect(Collectors.toList());
  }

  private List<AttentionSetUpdate> getAttentionSetUpdates(Change.Id changeId) {
    List<ChangeData> changeData = changeQueryProvider.get().byLegacyChangeId(changeId);
    if (changeData.size() != 1) {
      throw new IllegalStateException(
          String.format("Not exactly one change found for ID %s.", changeId));
    }
    return new ArrayList<>(Iterables.getOnlyElement(changeData).attentionSet());
  }

  private ReviewInput reviewWithComment() {
    return reviewInReplyToComment(null);
  }

  private ReviewInput reviewInReplyToComment(@Nullable String id) {
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.side = Side.REVISION;
    comment.path = Patch.COMMIT_MSG;
    comment.message = "comment";
    comment.setUpdated(TimeUtil.now());
    comment.inReplyTo = id;
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    return reviewInput;
  }

  private Correspondence<AttentionSetUpdate, Account.Id> hasAccount() {
    return NullAwareCorrespondence.transforming(AttentionSetUpdate::account, "hasAccount");
  }
}
