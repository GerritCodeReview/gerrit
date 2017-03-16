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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ChangeReviewersByEmailIT extends AbstractDaemonTest {

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.setEnableReviewerByEmail(true);
    saveProjectConfig(project, cfg);
  }

  @Test
  public void addByEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRFCAddressString(acc);
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
  public void removeByEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput addInput = new AddReviewerInput();
      addInput.reviewer = toRFCAddressString(acc);
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

    AddReviewerInput addInput = new AddReviewerInput();
    addInput.reviewer = toRFCAddressString(acc);
    addInput.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(addInput);

    AddReviewerInput modifyInput = new AddReviewerInput();
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

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRFCAddressString(acc);
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

      AddReviewerInput addInput = new AddReviewerInput();
      addInput.reviewer = toRFCAddressString(acc);
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

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRFCAddressString(acc);
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
  public void rejectMissingEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createChange();

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("email invalid");
    gApi.changes().id(r.getChangeId()).addReviewer("");
  }

  @Test
  public void rejectMalformedEmail() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createChange();

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("email invalid");
    gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@");
  }

  @Test
  public void rejectOnNonPublicChange() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();
    PushOneCommit.Result r = createDraftChange();

    exception.expect(BadRequestException.class);
    exception.expectMessage("change is not publicly visible");
    gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
  }

  @Test
  public void rejectWhenFeatureIsDisabled() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.setEnableReviewerByEmail(false);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();

    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage(
        "Foo Bar <foo.bar@gerritcodereview.com> does not identify a registered user or group");
    gApi.changes().id(r.getChangeId()).addReviewer("Foo Bar <foo.bar@gerritcodereview.com>");
  }

  @Test
  public void reviewersByEmailAreServedFromIndex() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    AccountInfo acc = new AccountInfo("Foo Bar", "foo.bar@gerritcodereview.com");

    for (ReviewerState state : ImmutableList.of(ReviewerState.CC, ReviewerState.REVIEWER)) {
      PushOneCommit.Result r = createChange();

      AddReviewerInput input = new AddReviewerInput();
      input.reviewer = toRFCAddressString(acc);
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

  private static String toRFCAddressString(AccountInfo info) {
    return (new Address(info.name, info.email)).toString();
  }
}
