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
import static com.google.gerrit.extensions.restapi.testing.AttentionSetUpdateSubject.assertThat;
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
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.EmailStrategy;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.FakeEmailSender;
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
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseClockStep(clockStepUnit = TimeUnit.MINUTES)
public class AttentionSetIT extends AbstractDaemonTest {

  @Inject private ChangeOperations changeOperations;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject private FakeEmailSender email;
  @Inject private TestCommentHelper testCommentHelper;
  @Inject private Provider<InternalChangeQuery> changeQueryProvider;

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
        change(r).addToAttentionSet(new AttentionSetInput(user.email(), "first"))._accountId;
    assertThat(accountId).isEqualTo(user.id().get());
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), user.id(), AttentionSetUpdate.Operation.ADD, "first");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Second add is ignored.
    accountId =
        change(r).addToAttentionSet(new AttentionSetInput(user.email(), "second"))._accountId;
    assertThat(accountId).isEqualTo(user.id().get());
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Only one email since the second add was ignored.
    String emailBody = Iterables.getOnlyElement(email.getMessages()).body();
    assertThat(emailBody)
        .contains(
            user.fullName()
                + " added themselves to the attention set of this change.\n The reason is: first.");
  }

  @Test
  public void addMultipleUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    Instant timestamp1 = fakeClock.now();
    int accountId1 =
        change(r).addToAttentionSet(new AttentionSetInput(user.email(), "user"))._accountId;
    assertThat(accountId1).isEqualTo(user.id().get());
    fakeClock.advance(Duration.ofSeconds(42));
    Instant timestamp2 = fakeClock.now();
    int accountId2 =
        change(r)
            .addToAttentionSet(new AttentionSetInput(admin.id().toString(), "admin"))
            ._accountId;
    assertThat(accountId2).isEqualTo(admin.id().get());

    AttentionSetUpdate expectedAttentionSetUpdate1 =
        AttentionSetUpdate.createFromRead(
            timestamp1, user.id(), AttentionSetUpdate.Operation.ADD, "user");
    AttentionSetUpdate expectedAttentionSetUpdate2 =
        AttentionSetUpdate.createFromRead(
            timestamp2, admin.id(), AttentionSetUpdate.Operation.ADD, "admin");
    assertThat(r.getChange().attentionSet())
        .containsExactly(expectedAttentionSetUpdate1, expectedAttentionSetUpdate2);
  }

  @Test
  public void removeUser() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "added"));
    requestScopeOperations.setApiUser(user.id());

    fakeClock.advance(Duration.ofSeconds(42));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed"));
    AttentionSetUpdate expectedAttentionSetUpdate =
        AttentionSetUpdate.createFromRead(
            fakeClock.now(), user.id(), AttentionSetUpdate.Operation.REMOVE, "removed");
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Second removal is ignored.
    fakeClock.advance(Duration.ofSeconds(42));
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("removed again"));
    assertThat(r.getChange().attentionSet()).containsExactly(expectedAttentionSetUpdate);

    // Only one email since the second remove was ignored.
    String emailBody = Iterables.getOnlyElement(email.getMessages()).body();
    assertThat(emailBody)
        .contains(
            user.fullName()
                + " removed themselves from the attention set of this change.\n"
                + " The reason is: removed.");
  }

  @Test
  public void removeUserWithInvalidUserInput() throws Exception {
    PushOneCommit.Result r = createChange();
    BadRequestException exception =
        assertThrows(
            BadRequestException.class,
            () ->
                change(r)
                    .attention(user.id().toString())
                    .remove(new AttentionSetInput("invalid user", "reason")));
    assertThat(exception.getMessage())
        .isEqualTo("The user specified in the input body couldn't be found.");

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
  public void removeUnrelatedUser() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).attention(user.id().toString()).remove(new AttentionSetInput("foo"));
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void abandonRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "user"));
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
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));

    change(r).setWorkInProgress();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("Change was marked work in progress");
  }

  @Test
  public void submitRemovesUsersForAllSubmittedChanges() throws Exception {
    PushOneCommit.Result r1 = createChange("refs/heads/master", "file1", "content");

    change(r1)
        .current()
        .review(ReviewInput.approve().addUserToAttentionSet(user.email(), "reason"));
    PushOneCommit.Result r2 = createChange("refs/heads/master", "file2", "content");
    change(r2)
        .current()
        .review(ReviewInput.approve().addUserToAttentionSet(user.email(), "reason"));

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
    PushOneCommit.Result r1 = createChange("refs/heads/master", "file1", "content");

    change(r1)
        .current()
        .review(ReviewInput.approve().addUserToAttentionSet(user.email(), "reason"));

    TestAccount robot =
        accountCreator.create(
            "robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users", "Administrators");
    requestScopeOperations.setApiUser(robot.id());
    change(r1).current().submit();

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r1, user));

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
    AddReviewerInput input = new AddReviewerInput();
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
    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.state = ReviewerState.REVIEWER;
    addReviewerInput.reviewer = user.email();
    reviewInput.reviewers = ImmutableList.of(addReviewerInput);

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
    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.state = ReviewerState.CC;
    addReviewerInput.reviewer = user.email();

    change(r).addReviewer(addReviewerInput);

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void ccsConsideredSameAsRemovedForExistingReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addReviewer(user.email());

    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.state = ReviewerState.CC;
    addReviewerInput.reviewer = user.email();
    change(r).addReviewer(addReviewerInput);

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
            "robot1", "robot1@example.com", "Ro Bot", "Ro", "Service Users", "Administrators");
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
  public void readyForReviewWhileRemovingReviewerRemovesThemToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    ReviewInput reviewInput = ReviewInput.create().setReady(true);
    AddReviewerInput addReviewerInput = new AddReviewerInput();
    addReviewerInput.state = ReviewerState.CC;
    addReviewerInput.reviewer = user.email();
    reviewInput.reviewers = ImmutableList.of(addReviewerInput);
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
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "user"));
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
    email.getMessages().isEmpty();
  }

  @Test
  public void reviewRemovesManuallyRemovedUserFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));
    requestScopeOperations.setApiUser(user.id());

    ReviewInput reviewInput =
        ReviewInput.create().removeUserFromAttentionSet(user.email(), "reason");
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");

    // No emails for removing from attention set were sent.
    email.getMessages().isEmpty();
  }

  @Test
  public void reviewWithManualAdditionToAttentionSetFailsWithoutReason() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));

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
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "addition"));

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
            "user can not be added/removed twice, and can not be added and removed at the same"
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
            "user can not be added/removed twice, and can not be added and removed at the same"
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
            "user can not be added/removed twice, and can not be added and removed at the same"
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

    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));
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
        accountCreator.create("robot1", "robot1@example.com", "Ro Bot", "Ro", "Service Users");
    PushOneCommit.Result r = createChange();

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
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
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
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    change(r).current().review(ReviewInput.recommend());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotReviewWithNegativeLabelAddsOwner() throws Exception {
    TestAccount robot =
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
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
  public void robotCommentAddsOwnerOnNewChanges() throws Exception {
    TestAccount robot =
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(robot.id());
    ReviewInput reviewInput = new ReviewInput();
    ReviewInput.RobotCommentInput robotCommentInput =
        TestCommentHelper.createRobotCommentInputWithMandatoryFields("a.txt");
    reviewInput.robotComments = ImmutableMap.of("a.txt", ImmutableList.of(robotCommentInput));
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(admin.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("A robot comment was added");
  }

  @Test
  public void robotCommentDoesNotAddOwnerOnClosedChanges() throws Exception {
    TestAccount robot =
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
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
        accountCreator.create("robot2", "robot2@example.com", "Ro Bot", "Ro", "Service Users");
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
    change(r).current().review(new ReviewInput().addUserToAttentionSet(user.email(), "reason"));

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet).hasAccountIdThat().isEqualTo(user.id());
    assertThat(attentionSet).hasOperationThat().isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet).hasReasonThat().isEqualTo("reason");
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
  public void attentionSetWithEmailFilter() throws Exception {
    PushOneCommit.Result r = createChange();

    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    requestScopeOperations.setApiUser(user.id());
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = EmailStrategy.ATTENTION_SET_ONLY;
    gApi.accounts().self().setPreferences(prefs);
    requestScopeOperations.setApiUser(admin.id());

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
  public void attentionSetWithEmailFilterImpactingOnlyChangeEmails() throws Exception {
    // Add preference for the user such that they only receive an email on changes that require
    // their attention.
    requestScopeOperations.setApiUser(user.id());
    GeneralPreferencesInfo prefs = gApi.accounts().self().getPreferences();
    prefs.emailStrategy = EmailStrategy.ATTENTION_SET_ONLY;
    gApi.accounts().self().setPreferences(prefs);
    requestScopeOperations.setApiUser(admin.id());

    // Ensure emails that don't relate to changes are still sent.
    gApi.accounts().id(user.id().get()).generateHttpPassword();
    assertThat(sender.getMessages()).isNotEmpty();
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
    comment.updated = TimeUtil.nowTs();
    comment.inReplyTo = id;
    ReviewInput reviewInput = new ReviewInput();
    reviewInput.comments = ImmutableMap.of(Patch.COMMIT_MSG, ImmutableList.of(comment));
    return reviewInput;
  }

  private Correspondence<AttentionSetUpdate, Account.Id> hasAccount() {
    return NullAwareCorrespondence.transforming(AttentionSetUpdate::account, "hasAccount");
  }
}
