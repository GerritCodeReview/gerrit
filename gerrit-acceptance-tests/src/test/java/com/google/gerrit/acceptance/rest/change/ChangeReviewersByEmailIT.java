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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ChangeReviewersByEmailIT extends AbstractDaemonTest {

  @Before
  public void setUp() throws Exception {
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);
  }

  @Test
  public void addByEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      ChangeInfo info =
          gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
      assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(acc)));
      // All reviewers added by email should be removable
      assertThat(info.removableReviewers).isEqualTo(ImmutableList.of(acc));
    }
  }

  @Test
  public void addByEmailAndById() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo byEmail = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");
    AccountInfo byId = new AccountInfo(user.id.get());

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput inputByEmail = new ReviewerInput();
      inputByEmail.reviewer = toRfcAddressString(byEmail);
      inputByEmail.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputByEmail);

      ReviewerInput inputById = new ReviewerInput();
      inputById.reviewer = user.email;
      inputById.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputById);

      ChangeInfo info =
          gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
      assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(byId, byEmail)));
      // All reviewers (both by id and by email) should be removable
      assertThat(info.removableReviewers).isEqualTo(ImmutableList.of(byId, byEmail));
    }
  }

  @Test
  public void removeByEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput addInput = new ReviewerInput();
      addInput.reviewer = toRfcAddressString(acc);
      addInput.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(addInput);

      gApi.changes().id(r.getChangeId()).reviewer(acc.email).remove();

      ChangeInfo info =
          gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
      assertThat(info.reviewers).isEmpty();
    }
  }

  @Test
  public void convertFromCCToReviewer() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    PushOneCommit.Result r = createChange();

    ReviewerInput addInput = new ReviewerInput();
    addInput.reviewer = toRfcAddressString(acc);
    addInput.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(addInput);

    ReviewerInput modifyInput = new ReviewerInput();
    modifyInput.reviewer = addInput.reviewer;
    modifyInput.state = ReviewerState.REVIEWER;
    gApi.changes().id(r.getChangeId()).addReviewer(modifyInput);

    ChangeInfo info =
        gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    assertThat(info.reviewers)
        .isEqualTo(ImmutableMap.of(ReviewerState.REVIEWER, ImmutableList.of(acc)));
  }

  @Test
  public void addedReviewersGetNotified() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      List<Message> messages = sender.getMessages();
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).rcpt()).containsExactly(Address.parse(input.reviewer));
      sender.clear();
    }
  }

  @Test
  public void removingReviewerTriggersNotification() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput addInput = new ReviewerInput();
      addInput.reviewer = toRfcAddressString(acc);
      addInput.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(addInput);

      // Review change as user
      ReviewInput reviewInput = new ReviewInput();
      reviewInput.message = "I have a comment";
      setApiUser(user);
      revision(r).review(reviewInput);
      setApiUser(admin);

      sender.clear();

      // Delete as admin
      gApi.changes().id(r.getChangeId()).reviewer(addInput.reviewer).remove();

      List<Message> messages = sender.getMessages();
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).rcpt())
          .containsExactly(Address.parse(addInput.reviewer), user.emailAddress);
      sender.clear();
    }
  }

  @Test
  public void reviewerAndCCReceiveRegularNotification() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);
      sender.clear();

      gApi.changes()
          .id(r.getChangeId())
          .revision(r.getCommit().name())
          .review(ReviewInput.approve());

      if (state == ReviewerState.CC) {
        assertNotifyCc(Address.parse(input.reviewer));
      } else {
        assertNotifyTo(Address.parse(input.reviewer));
      }
    }
  }

  @Test
  public void reviewerAndCCReceiveSameEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    PushOneCommit.Result r = createChange();
    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      for (int i = 0; i < 10; i++) {
        ReviewerInput input = new ReviewerInput();
        input.reviewer = String.format("%s-%s@gerritcodereview.com", state, i);
        input.state = state;
        gApi.changes().id(r.getChangeId()).addReviewer(input);
      }
    }

    // Also add user as a regular reviewer
    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email;
    input.state = ReviewerState.REVIEWER;
    gApi.changes().id(r.getChangeId()).addReviewer(input);

    sender.clear();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    // Assert that only one email was sent out to everyone
    assertThat(sender.getMessages()).hasSize(1);
  }

  @Test
  public void addingMultipleReviewersAndCCsAtOnceSendsOnlyOneEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    PushOneCommit.Result r = createChange();
    ReviewInput reviewInput = new ReviewInput();
    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      for (int i = 0; i < 10; i++) {
        reviewInput.reviewer(String.format("%s-%s@gerritcodereview.com", state, i), state, true);
      }
    }
    assertThat(reviewInput.reviewers).hasSize(20);

    sender.clear();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(reviewInput);
    assertThat(sender.getMessages()).hasSize(1);
  }

  @Test
  public void rejectMissingEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createChange();

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("");
    assertThat(result.error).isEqualTo(" is not a valid user identifier");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectMalformedEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createChange();

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@");
    assertThat(result.error).isEqualTo("Foo Bar <foo.bar@ is not a valid user identifier");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectOnNonPublicChange() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createDraftChange();

    ReviewerResult result =
        gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
    assertThat(result.error)
        .isEqualTo(
            "Foo Bar <foo.bar@gerritcodereview.com> does not have permission to see this change");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectWhenFeatureIsDisabled() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.FALSE;
    gApi.projects().name(project.get()).config(conf);

    PushOneCommit.Result r = createChange();

    ReviewerResult result =
        gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
    assertThat(result.error)
        .isEqualTo(
            "Foo Bar <foo.bar@gerritcodereview.com> does not identify a registered user or group");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void reviewersByEmailAreServedFromIndex() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      notesMigration.setFailOnLoad(true);
      try {
        ChangeInfo info =
            Iterables.getOnlyElement(
                gApi.changes()
                    .query(r.getChangeId())
                    .withOption(ListChangesOption.DETAILED_LABELS)
                    .get());
        assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(acc)));
      } finally {
        notesMigration.setFailOnLoad(false);
      }
    }
  }

  @Test
  public void removingReviewerByEmailOnPostReview() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      // Add user as reviewer/CC
      ReviewInput reviewInput = new ReviewInput();
      reviewInput.reviewer("foo@bar.com", state, true);
      reviewInput.notify = NotifyHandling.NONE;
      gApi.changes().id(r.getChangeId()).current().review(reviewInput);

      // Verify addition
      Map<ReviewerState, Collection<AccountInfo>> reviewers =
          gApi.changes().id(r.getChangeId()).get().reviewers;
      assertThat(reviewers.keySet()).contains(state);
      assertThat(reviewers.get(state).stream().map(a -> a.email).collect(toList()))
          .contains("foo@bar.com");

      // Remove user as reviewer
      ReviewInput reviewInputForRemoval = new ReviewInput();
      reviewInputForRemoval.reviewer("foo@bar.com", ReviewerState.REMOVED, true);
      reviewInputForRemoval.notify = NotifyHandling.NONE;
      gApi.changes().id(r.getChangeId()).current().review(reviewInputForRemoval);

      // Verify removal
      Map<ReviewerState, Collection<AccountInfo>> reviewersWithRemoval =
          gApi.changes().id(r.getChangeId()).get().reviewers;
      assertThat(reviewersWithRemoval.keySet()).containsExactly(ReviewerState.REVIEWER);
      if (state == ReviewerState.REVIEWER) {
        assertThat(reviewersWithRemoval.get(state).stream().map(a -> a.email).collect(toList()))
            .doesNotContain("foo@bar.com");
      }
    }
  }

  @Test
  public void mutateReviewerStateOnReviewerOnPostReview() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    for (ReviewerState startState : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      // Add user as reviewer/CC
      ReviewInput reviewInput = new ReviewInput();
      reviewInput.reviewer("foo@bar.com", startState, true);
      reviewInput.notify = NotifyHandling.NONE;
      gApi.changes().id(r.getChangeId()).current().review(reviewInput);

      // Verify addition
      Map<ReviewerState, Collection<AccountInfo>> reviewers =
          gApi.changes().id(r.getChangeId()).get().reviewers;
      assertThat(reviewers.keySet()).contains(startState);
      assertThat(reviewers.get(startState).stream().map(a -> a.email).collect(toList()))
          .contains("foo@bar.com");

      // Mutate user state to be CC
      ReviewerState targetState =
          startState == ReviewerState.CC ? ReviewerState.REVIEWER : ReviewerState.CC;
      ReviewInput reviewInputForMutation = new ReviewInput();
      reviewInputForMutation.reviewer("foo@bar.com", targetState, true);
      reviewInputForMutation.notify = NotifyHandling.NONE;
      gApi.changes().id(r.getChangeId()).current().review(reviewInputForMutation);

      // Verify mutation
      Map<ReviewerState, Collection<AccountInfo>> reviewersWithMutation =
          gApi.changes().id(r.getChangeId()).get().reviewers;
      assertThat(reviewersWithMutation.keySet()).contains(targetState);
      assertThat(
              reviewersWithMutation.get(targetState).stream().map(a -> a.email).collect(toList()))
          .contains("foo@bar.com");
    }
  }

  private static String toRfcAddressString(AccountInfo info) {
    return (new Address(info.name, info.email)).toString();
  }
}
