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
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.mail.Address;
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

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS);
      assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(acc)));
      // All reviewers added by email should be removable
      assertThat(info.removableReviewers).isEqualTo(ImmutableList.of(acc));
    }
  }

  @Test
  public void addByEmailAndById() throws Exception {
    AccountInfo byEmail = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");
    AccountInfo byId = new AccountInfo(user.id().get());

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput inputByEmail = new AddReviewerInput();
      inputByEmail.reviewer = toRfcAddressString(byEmail);
      inputByEmail.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputByEmail);

      AddReviewerInput inputById = new AddReviewerInput();
      inputById.reviewer = user.email();
      inputById.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(inputById);

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS);
      assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(byId, byEmail)));
      // All reviewers (both by id and by email) should be removable
      assertThat(info.removableReviewers).isEqualTo(ImmutableList.of(byId, byEmail));
    }
  }

  @Test
  public void listReviewersByEmail() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
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

      AddReviewerInput addInput = new AddReviewerInput();
      addInput.reviewer = toRfcAddressString(acc);
      addInput.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(addInput);

      gApi.changes().id(r.getChangeId()).reviewer(acc.email).remove();

      ChangeInfo info = gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS);
      assertThat(info.reviewers).isEmpty();
    }
  }

  @Test
  public void convertFromCCToReviewer() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    PushOneCommit.Result r = createChange();

    AddReviewerInput addInput = new AddReviewerInput();
    addInput.reviewer = toRfcAddressString(acc);
    addInput.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(addInput);

    AddReviewerInput modifyInput = new AddReviewerInput();
    modifyInput.reviewer = addInput.reviewer;
    modifyInput.state = ReviewerState.REVIEWER;
    gApi.changes().id(r.getChangeId()).addReviewer(modifyInput);

    ChangeInfo info = gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS);
    assertThat(info.reviewers)
        .isEqualTo(ImmutableMap.of(ReviewerState.REVIEWER, ImmutableList.of(acc)));
  }

  @Test
  public void addedReviewersGetNotified() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
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

      AddReviewerInput addInput = new AddReviewerInput();
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
          .containsExactly(Address.parse(addInput.reviewer), user.getEmailAddress());
      sender.clear();
    }
  }

  @Test
  public void reviewerAndCCReceiveRegularNotification() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
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
        AddReviewerInput input = new AddReviewerInput();
        input.reviewer = String.format("%s-%s@gerritcodereview.com", state, i);
        input.state = state;
        gApi.changes().id(r.getChangeId()).addReviewer(input);
      }
    }

    // Also add user as a regular reviewer
    AddReviewerInput input = new AddReviewerInput();
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

    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("");
    assertThat(result.error).isEqualTo("Empty input provided for reviewer");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectMalformedEmail() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@");
    assertThat(result.error)
        .isEqualTo(
            "Account 'Foo Bar <foo.bar@' not found\n"
                + "Group Not Found: Foo Bar <foo.bar@\n"
                + "'Foo Bar <foo.bar@' does not appear to contain an email address");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void rejectWhenFeatureIsDisabled() throws Exception {
    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.FALSE;
    gApi.projects().name(project.get()).config(conf);

    PushOneCommit.Result r = createChange();

    AddReviewerResult result =
        gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
    assertThat(result.error)
        .isEqualTo(
            "Account 'Foo Bar <foo.bar@gerritcodereview.com>' not found\n"
                + "Group Not Found: Foo Bar <foo.bar@gerritcodereview.com>\n"
                + "Adding reviewers by email is not enabled on this project");
    assertThat(result.reviewers).isNull();
  }

  @Test
  public void reviewersByEmailAreServedFromIndex() throws Exception {
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRfcAddressString(acc);
      input.state = state;
      gApi.changes().id(r.getChangeId()).addReviewer(input);

      try (AutoCloseable ignored = disableNoteDb()) {
        ChangeInfo info =
            Iterables.getOnlyElement(
                gApi.changes().query(r.getChangeId()).withOption(DETAILED_LABELS).get());
        assertThat(info.reviewers).isEqualTo(ImmutableMap.of(state, ImmutableList.of(acc)));
      }
    }
  }

  @Test
  public void addExistingReviewerByEmailShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = "nonexisting@example.com";
    input.state = ReviewerState.REVIEWER;

    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);
    ReviewerInfo info = result.reviewers.get(0);
    assertThat(info._accountId).isNull();
    assertThat(info.email).isEqualTo(input.reviewer);

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).reviewers).isEmpty();
  }

  @Test
  public void addExistingCcByEmailShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = "nonexisting@example.com";
    input.state = ReviewerState.CC;
    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);

    assertThat(result.ccs).hasSize(1);
    AccountInfo info = result.ccs.get(0);
    assertThat(info._accountId).isNull();
    assertThat(info.email).isEqualTo(input.reviewer);

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).ccs).isEmpty();
  }

  private static String toRfcAddressString(AccountInfo info) {
    return (new Address(info.name, info.email)).toString();
  }
}
