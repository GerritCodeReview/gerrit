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
import static com.google.gerrit.extensions.client.ListChangesOption.LABELS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Address;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ChangeReviewersByEmailIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);
  }

  @Test
  public void addByEmail() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(LABELS);
      assertThat(info.reviewers).containsExactly(state, ImmutableList.of(acc));
      // All reviewers added by email should be removable
      assertThat(info.removableReviewers).containsExactly(acc);
    }
  }

  @Test
  public void addByEmailAndById() throws Exception {
    AccountInfo byEmail = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");
    AccountInfo byId = new AccountInfo(user.id().get());

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput inputByEmail = new ReviewerInput();
      inputByEmail.reviewer = toRfcAddressString(byEmail);
      inputByEmail.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputByEmail);

      ReviewerInput inputById = new ReviewerInput();
      inputById.reviewer = user.email();
      inputById.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputById);

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(LABELS);
      assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(byId, byEmail)));
      // All reviewers (both by id and by email) should be removable
      assertThat(info.removableReviewers).containsExactly(byId, byEmail);
    }
  }

  @Test
  public void listReviewersByEmail() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      RestResponse restResponse =
          adminRestSession.get("/changes/" + r.getChangeId() + "/reviewers/");
      restResponse.assertOK();
      Type type = new TypeToken<List<ReviewerInfo>>() {}.getType();
      List<ReviewerInfo> reviewers = newGson().fromJson(restResponse.getReader(), type);
      restResponse.consume();

      assertThat(reviewers).hasSize(1);
      ReviewerInfo reviewerInfo = Iterables.getOnlyElement(reviewers);
      assertThat(reviewerInfo._accountId).isNull();
      assertThat(reviewerInfo.name).isEqualTo(acc.name);
      assertThat(reviewerInfo.email).isEqualTo(acc.email);
    }
  }

  @Test
  public void removeByEmail() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput addInput = new ReviewerInput();
      addInput.reviewer = toRfcAddressString(acc);
      addInput.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(addInput);

      gApi.changes().id(r.getChangeId()).reviewer(acc.email).remove();

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(LABELS);
      assertThat(info.reviewers).isEmpty();
    }
  }

  @Test
  public void convertFromCCToReviewer() throws Exception {
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

    ChangeInfo info = gApi.changes().id(r.getChangeId()).get(LABELS);
    assertThat(info.reviewers)
        .isEqualTo(ImmutableMap.of(ReviewerState.REVIEWER, ImmutableList.of(acc)));
  }

  @Test
  public void addedReviewersGetNotified() throws Exception {
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
      requestScopeOperations.setApiUser(user.id());
      revision(r).review(reviewInput);
      requestScopeOperations.setApiUser(admin.id());

      sender.clear();

      // Delete as admin
      gApi.changes().id(r.getChangeId()).reviewer(addInput.reviewer).remove();

      List<Message> messages = sender.getMessages();
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).rcpt())
          .containsExactly(Address.parse(addInput.reviewer), user.getNameEmail());
      sender.clear();
    }
  }

  @Test
  public void reviewerAndCCReceiveRegularNotification() throws Exception {
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

      assertNotifyCc(Address.parse(input.reviewer));
    }
  }

  @Test
  public void reviewerAndCCReceiveSameEmail() throws Exception {
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
    input.reviewer = user.email();
    input.state = ReviewerState.REVIEWER;
    gApi.changes().id(r.getChangeId()).addReviewer(input);

    sender.clear();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    // Assert that only one email was sent out to everyone
    assertThat(sender.getMessages()).hasSize(1);
  }

  @Test
  public void addingMultipleReviewersAndCCsAtOnceSendsOnlyOneEmail() throws Exception {
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
    PushOneCommit.Result r = createChange();

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("");
    assertThat(result.error).isEqualTo(" is not a valid user identifier");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectMalformedEmail() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@");
    assertThat(result.error).isEqualTo("Foo Bar <foo.bar@ is not a valid user identifier");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectWhenFeatureIsDisabled() throws Exception {
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.FALSE;
    gApi.projects().name(project.get()).config(conf);

    PushOneCommit.Result r = createChange();

    ReviewerResult result =
        gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
    assertThat(result.error)
        .isEqualTo(
            "Account 'Foo Bar <foo.bar@gerritcodereview.com>' not found\n"
                + "Foo Bar <foo.bar@gerritcodereview.com> does not identify a registered user or"
                + " group");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void reviewersByEmailAreServedFromIndex() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      ReviewerInput input = new ReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      try (AutoCloseable ignored = disableNoteDb()) {
        ChangeInfo info =
            Iterables.getOnlyElement(
                gApi.changes().query(r.getChangeId()).withOption(LABELS).get());
        assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(acc)));
      }
    }
  }

  @Test
  public void addExistingReviewerByEmailShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewerInput input = new ReviewerInput();
    input.reviewer = "nonexisting@example.com";
    input.state = ReviewerState.REVIEWER;

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);
    ReviewerInfo info = result.reviewers.get(0);
    assertThat(info._accountId).isNull();
    assertThat(info.email).isEqualTo(input.reviewer);

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).reviewers).isEmpty();
  }

  @Test
  public void addExistingCcByEmailShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewerInput input = new ReviewerInput();
    input.reviewer = "nonexisting@example.com";
    input.state = ReviewerState.CC;
    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);

    assertThat(result.ccs).hasSize(1);
    AccountInfo info = result.ccs.get(0);
    assertThat(info._accountId).isNull();
    assertThat(info.email).isEqualTo(input.reviewer);

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).ccs).isEmpty();
  }

  private static String toRfcAddressString(AccountInfo info) {
    return Address.create(info.name, info.email).toString();
  }
}
