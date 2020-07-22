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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseClockStep(clockStepUnit = TimeUnit.MINUTES)
public class AttentionSetIT extends AbstractDaemonTest {

  @Inject private RequestScopeOperations requestScopeOperations;

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
    assertThat(userUpdate.account()).isEqualTo(user.id());
    assertThat(userUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(userUpdate.reason()).isEqualTo("Change was abandoned");

    AttentionSetUpdate adminUpdate =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(adminUpdate.account()).isEqualTo(admin.id());
    assertThat(adminUpdate.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(adminUpdate.reason()).isEqualTo("Change was abandoned");
  }

  @Test
  public void workInProgressRemovesUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));

    change(r).setWorkInProgress();

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was marked work in progress");
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

    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was submitted");

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r2, user));

    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Change was submitted");
  }

  /**
   * There is currently a bug that adds the person who submitted the change as reviewer, which in
   * turn adds them to the attention set. This test ensures this doesn't happen.
   */
  @Test
  public void submitDoesNotAddReviewersToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange("refs/heads/master", "file1", "content");

    // Someone else approves, because if admin reviews, they will be added to the reviewers (and the
    // bug won't be reproduced).
    requestScopeOperations.setApiUser(accountCreator.admin2().id());
    change(r).current().review(ReviewInput.approve().addUserToAttentionSet(user.email(), "reason"));

    requestScopeOperations.setApiUser(admin.id());

    change(r).attention(admin.email()).remove(new AttentionSetInput("remove"));
    change(r).current().submit();

    // Attention set updates that relate to the admin (the person who replied) are filtered out.
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));

    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("remove");

    change(r).addReviewer(user.email());
  }

  @Test
  public void addedReviewersAreAddedToAttentionSetOnMergedChanges() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).current().review(ReviewInput.approve());
    change(r).current().submit();

    change(r).addReviewer(user.email());
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));

    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was added");
  }

  @Test
  public void reviewersAddedAndRemovedFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).addReviewer(user.id().toString());

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was added");

    change(r).reviewer(user.email()).remove();

    attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was removed");
  }

  @Test
  public void reviewersAddedAndRemovedByEmailFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    change(r).addReviewer(user.email());

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was added");

    change(r).reviewer(user.email()).remove();

    attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was removed");
  }

  @Test
  public void reviewersInWorkProgressNotAddedToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void addingReviewerWhileMarkingWorkInprogressDoesntAddToAttentionSet() throws Exception {
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason())
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was removed");
  }

  @Test
  public void readyForReviewAddsAllReviewersToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();
    change(r).addReviewer(user.email());

    change(r).setReadyForReview();
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("Change was marked ready for review");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was removed");
  }

  @Test
  public void readyForReviewWhileAddingReviewerAddsThemToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).setWorkInProgress();

    ReviewInput reviewInput = ReviewInput.create().setReady(true).reviewer(user.email());
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was added");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("removed");
  }

  @Test
  public void reviewAddsManuallyAddedUserToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet(user.email(), "reason");

    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reason");
  }

  @Test
  public void reviewRemovesManuallyRemovedUserFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));

    ReviewInput reviewInput =
        ReviewInput.create().removeUserFromAttentionSet(user.email(), "reason");
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("reason");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("reason");
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
            "user can not be added/removed twice, and can not be added and removed at the same time");
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
            "user can not be added/removed twice, and can not be added and removed at the same time");
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
            "user can not be added/removed twice, and can not be added and removed at the same time");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("reason");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reason");
  }

  @Test
  public void reviewRemovesUserFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("removed on reply");
  }

  @Test
  public void reviewAddUserToAttentionSetWhileReplyingJustAddsUser() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewInput reviewInput = ReviewInput.create().addUserToAttentionSet(admin.email(), "reason");
    change(r).current().review(reviewInput);

    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reason");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("removed on reply");
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
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reviewer or cc replied");
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
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reviewer or cc replied");
  }

  @Test
  public void repliesAddsOwnerAndUploader() throws Exception {
    // Create change with owner: admin
    PushOneCommit.Result r = createChange();

    // Clone, fetch, and checkout the change with user, and then create a new patchset.
    TestRepository<InMemoryRepository> repo = cloneProject(project, user);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master",
            user,
            repo,
            "new subject",
            "new file",
            "new content");

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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reviewer or cc replied");

    // Owner added
    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reviewer or cc replied");
  }

  @Test
  public void ownerRepliesAddsReviewersOnly() throws Exception {
    PushOneCommit.Result r = createChange();
    // add reviewer and cc
    change(r).addReviewer(user.email());
    change(r)
        .attention(user.email())
        .remove(new AttentionSetInput("Reviewer is not in attention-set"));

    TestAccount cc = accountCreator.admin2();
    AddReviewerInput input = new AddReviewerInput();
    input.state = ReviewerState.CC;
    input.reviewer = cc.email();
    change(r).addReviewer(input);

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    // cc not added
    assertThat(getAttentionSetUpdatesForUser(r, cc)).isEmpty();

    // reviewer added
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("owner or uploader replied");
  }

  @Test
  public void ownerRepliesWhileRemovingReviewerStillRemovesFromAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addReviewer(user.email());

    ReviewInput reviewInput = ReviewInput.create().reviewer(user.email(), ReviewerState.CC, false);
    change(r).current().review(reviewInput);

    // cc removed
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, user));
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("Reviewer was removed");
  }

  @Test
  public void uploaderRepliesAddsOwnerAndReviewersOnly() throws Exception {
    PushOneCommit.Result r = createChange();

    // Clone, fetch, and checkout the change with user, and then create a new patchset.
    TestRepository<InMemoryRepository> repo = cloneProject(project, user);
    GitUtil.fetch(repo, "refs/*:refs/*");
    repo.reset(r.getCommit());
    r =
        amendChange(
            r.getChangeId(),
            "refs/for/master",
            user,
            repo,
            "new subject",
            "new file",
            "new content");

    // Add reviewer and cc
    TestAccount reviewer = accountCreator.user2();
    change(r).addReviewer(reviewer.email());
    TestAccount cc = accountCreator.admin2();
    AddReviewerInput input = new AddReviewerInput();
    input.state = ReviewerState.CC;
    input.reviewer = cc.email();
    change(r).addReviewer(input);

    requestScopeOperations.setApiUser(user.id());
    change(r).attention(reviewer.email()).remove(new AttentionSetInput("reason"));

    ReviewInput reviewInput = new ReviewInput();
    change(r).current().review(reviewInput);

    // cc not added
    assertThat(getAttentionSetUpdatesForUser(r, cc)).isEmpty();

    // reviewer added
    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, reviewer));
    assertThat(attentionSet.account()).isEqualTo(reviewer.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("owner or uploader replied");

    // Owner added
    attentionSet = Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("uploader replied");
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
    assertThat(attentionSet.account()).isEqualTo(user.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("removed on reply");
  }

  @Test
  public void attentionSetUnchangedWithIgnoreDefaultAttentionSetRules() throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .reviewer(admin.email(), ReviewerState.CC, false)
                .blockDefaultAttentionSetRules());

    // admin is still in the attention set, although replies remove from attention set, and removing
    // from reviewer also should remove from attention set.
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.ADD);
    assertThat(attentionSet.reason()).isEqualTo("reason");
  }

  @Test
  public void attentionSetStillChangesWithIgnoreDefaultAttentionSetRulesWithInputList()
      throws Exception {
    PushOneCommit.Result r = createChange();
    change(r).addToAttentionSet(new AttentionSetInput(admin.email(), "reason"));
    change(r)
        .current()
        .review(
            ReviewInput.create()
                .removeUserFromAttentionSet(admin.email(), "removed")
                .blockDefaultAttentionSetRules());

    // Admin is still removed although we block default attention set rules, since we remove
    // the admin manually.
    AttentionSetUpdate attentionSet = Iterables.getOnlyElement(r.getChange().attentionSet());
    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("removed");
  }

  @Test
  public void robotsNotAddedToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();

    gApi.groups().id("Non-Interactive Users").addMembers(String.valueOf(user.id().get()));
    // TODO(paiking): Adding robots explicitly should throw an exception and only implicit additions
    // should fail silently.
    change(r).addToAttentionSet(new AttentionSetInput(user.email(), "reason"));
    change(r).addReviewer(user.email());

    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  @Test
  public void robotReviewDoesNotChangeAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange();
    // Admin is a robot
    gApi.groups().id("Non-Interactive Users").addMembers(String.valueOf(admin.id().get()));
    change(r).addReviewer(user.id().toString());
    assertThat(r.getChange().attentionSet()).isEmpty();
  }

  private List<AttentionSetUpdate> getAttentionSetUpdatesForUser(
      PushOneCommit.Result r, TestAccount account) {
    return r.getChange().attentionSet().stream()
        .filter(a -> a.account().get() == account.id().get())
        .collect(Collectors.toList());
  }
}
