// Copyright (C) 2016 The Android Open Source Project
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
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REMOVED;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ReviewerUpdateInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gson.stream.JsonReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ChangeReviewersIT extends AbstractDaemonTest {
  @Test
  public void addGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");

    int largeGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users = createAccounts(largeGroupSize, "addGroupAsReviewer");
    List<String> largeGroupUsernames = new ArrayList<>(mediumGroupSize);
    for (TestAccount u : users) {
      largeGroupUsernames.add(u.username);
    }
    List<String> mediumGroupUsernames = largeGroupUsernames.subList(0, mediumGroupSize);
    gApi.groups()
        .id(largeGroup)
        .addMembers(largeGroupUsernames.toArray(new String[largeGroupSize]));
    gApi.groups()
        .id(mediumGroup)
        .addMembers(mediumGroupUsernames.toArray(new String[mediumGroupSize]));

    // Attempt to add overly large group as reviewers.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerResult result = addReviewer(changeId, largeGroup);
    assertThat(result.input).isEqualTo(largeGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).contains("has too many members to add them all as reviewers");
    assertThat(result.reviewers).isNull();

    // Attempt to add medium group without confirmation.
    result = addReviewer(changeId, mediumGroup);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isTrue();
    assertThat(result.error)
        .contains("has " + mediumGroupSize + " members. Do you want to add them all as reviewers?");
    assertThat(result.reviewers).isNull();

    // Add medium group with confirmation.
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = mediumGroup;
    in.confirmed = true;
    result = addReviewer(changeId, in);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).hasSize(mediumGroupSize);

    // Verify that group members were added as reviewers.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, users.subList(0, mediumGroupSize));
  }

  @Test
  public void addCcAccount() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    in.state = CC;
    AddReviewerResult result = addReviewer(changeId, in);

    assertThat(result.input).isEqualTo(user.email);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertThat(result.reviewers).isNull();
      assertThat(result.ccs).hasSize(1);
      AccountInfo ai = result.ccs.get(0);
      assertThat(ai._accountId).isEqualTo(user.id.get());
      assertReviewers(c, CC, user);
    } else {
      assertThat(result.ccs).isNull();
      assertThat(result.reviewers).hasSize(1);
      AccountInfo ai = result.reviewers.get(0);
      assertThat(ai._accountId).isEqualTo(user.id.get());
      assertReviewers(c, REVIEWER, user);
    }

    // Verify email was sent to CCed account.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    if (notesMigration.readChanges()) {
      assertThat(m.body()).contains(admin.fullName + " has uploaded this change for review.");
    } else {
      assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
      assertThat(m.body()).contains("I'd like you to do a code review.");
    }
  }

  @Test
  public void addCcGroup() throws Exception {
    List<TestAccount> users = createAccounts(6, "addCcGroup");
    List<String> usernames = new ArrayList<>(6);
    for (TestAccount u : users) {
      usernames.add(u.username);
    }

    List<TestAccount> firstUsers = users.subList(0, 3);
    List<String> firstUsernames = usernames.subList(0, 3);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = createGroup("cc1");
    in.state = CC;
    gApi.groups()
        .id(in.reviewer)
        .addMembers(firstUsernames.toArray(new String[firstUsernames.size()]));
    AddReviewerResult result = addReviewer(changeId, in);

    assertThat(result.input).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    if (notesMigration.readChanges()) {
      assertThat(result.reviewers).isNull();
    } else {
      assertThat(result.ccs).isNull();
    }
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, CC, firstUsers);
    } else {
      assertReviewers(c, REVIEWER, firstUsers);
      assertReviewers(c, CC);
    }

    // Verify emails were sent to each of the group's accounts.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    List<Address> expectedAddresses = new ArrayList<>(firstUsers.size());
    for (TestAccount u : firstUsers) {
      expectedAddresses.add(u.emailAddress);
    }
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);

    // CC a group that overlaps with some existing reviewers and CCed accounts.
    TestAccount reviewer =
        accountCreator.create(name("reviewer"), "addCcGroup-reviewer@example.com", "Reviewer");
    result = addReviewer(changeId, reviewer.username);
    assertThat(result.error).isNull();
    sender.clear();
    in.reviewer = createGroup("cc2");
    gApi.groups().id(in.reviewer).addMembers(usernames.toArray(new String[usernames.size()]));
    gApi.groups().id(in.reviewer).addMembers(reviewer.username);
    result = addReviewer(changeId, in);
    assertThat(result.input).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertThat(result.ccs).hasSize(3);
      assertThat(result.reviewers).isNull();
      assertReviewers(c, REVIEWER, reviewer);
      assertReviewers(c, CC, users);
    } else {
      assertThat(result.ccs).isNull();
      assertThat(result.reviewers).hasSize(3);
      List<TestAccount> expectedUsers = new ArrayList<>(users.size() + 2);
      expectedUsers.addAll(users);
      expectedUsers.add(reviewer);
      assertReviewers(c, REVIEWER, expectedUsers);
    }

    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    expectedAddresses = new ArrayList<>(4);
    for (int i = 0; i < 3; i++) {
      expectedAddresses.add(users.get(users.size() - i - 1).emailAddress);
    }
    if (!notesMigration.readChanges()) {
      for (int i = 0; i < 3; i++) {
        expectedAddresses.add(users.get(i).emailAddress);
      }
    }
    expectedAddresses.add(reviewer.emailAddress);
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);
  }

  @Test
  public void transitionCcToReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    in.state = CC;
    addReviewer(changeId, in);
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER);
      assertReviewers(c, CC, user);
    } else {
      assertReviewers(c, REVIEWER, user);
      assertReviewers(c, CC);
    }

    in.state = REVIEWER;
    addReviewer(changeId, in);
    c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, user);
    assertReviewers(c, CC);
  }

  @Test
  public void driveByComment() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // Post drive-by message as user.
    ReviewInput input = new ReviewInput().message("hello");
    RestResponse resp =
        userRestSession.post(
            "/changes/" + r.getChangeId() + "/revisions/" + r.getCommit().getName() + "/review",
            input);
    ReviewResult result = readContentFromJson(resp, 200, ReviewResult.class);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNull();

    // Verify user is added to CC list.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER);
      assertReviewers(c, CC, user);
    } else {
      // If we aren't reading from NoteDb, the user will appear as a
      // reviewer.
      assertReviewers(c, REVIEWER, user);
      assertReviewers(c, CC);
    }
  }

  @Test
  public void addSelfAsReviewer() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // user adds self as REVIEWER.
    ReviewInput input = new ReviewInput().reviewer(user.username);
    RestResponse resp =
        userRestSession.post(
            "/changes/" + r.getChangeId() + "/revisions/" + r.getCommit().getName() + "/review",
            input);
    ReviewResult result = readContentFromJson(resp, 200, ReviewResult.class);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);

    // Verify reviewer state.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, user);
    assertReviewers(c, CC);
    LabelInfo label = c.labels.get("Code-Review");
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(1);
    ApprovalInfo approval = label.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.getId().get());
  }

  @Test
  public void addSelfAsCc() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // user adds self as CC.
    ReviewInput input = new ReviewInput().reviewer(user.username, CC, false);
    RestResponse resp =
        userRestSession.post(
            "/changes/" + r.getChangeId() + "/revisions/" + r.getCommit().getName() + "/review",
            input);
    ReviewResult result = readContentFromJson(resp, 200, ReviewResult.class);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);

    // Verify reviewer state.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER);
      assertReviewers(c, CC, user);
      // Verify no approvals were added.
      assertThat(c.labels).isNotNull();
      LabelInfo label = c.labels.get("Code-Review");
      assertThat(label).isNotNull();
      assertThat(label.all).isNull();
    } else {
      // When approvals are stored in ReviewDb, we still create a label for
      // the reviewing user, and force them into the REVIEWER state.
      assertReviewers(c, REVIEWER, user);
      assertReviewers(c, CC);
      LabelInfo label = c.labels.get("Code-Review");
      assertThat(label).isNotNull();
      assertThat(label.all).isNotNull();
      assertThat(label.all).hasSize(1);
      ApprovalInfo approval = label.all.get(0);
      assertThat(approval._accountId).isEqualTo(user.getId().get());
    }
  }

  @Test
  public void reviewerReplyWithoutVote() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // Verify reviewer state.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC);
    LabelInfo label = c.labels.get("Code-Review");
    assertThat(label).isNotNull();
    assertThat(label.all).isNull();

    // Add user as REVIEWER.
    ReviewInput input = new ReviewInput().reviewer(user.username);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);

    // Verify reviewer state. Both admin and user should be REVIEWERs now,
    // because admin gets forced into REVIEWER state by virtue of being owner.
    c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, admin, user);
    assertReviewers(c, CC);
    label = c.labels.get("Code-Review");
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(2);
    Map<Integer, Integer> approvals = new HashMap<>();
    for (ApprovalInfo approval : label.all) {
      approvals.put(approval._accountId, approval.value);
    }
    assertThat(approvals).containsEntry(admin.getId().get(), 0);
    assertThat(approvals).containsEntry(user.getId().get(), 0);

    // Comment as user without voting. This should delete the approval and
    // then replace it with the default value.
    input = new ReviewInput().message("hello");
    RestResponse resp =
        userRestSession.post(
            "/changes/" + r.getChangeId() + "/revisions/" + r.getCommit().getName() + "/review",
            input);
    result = readContentFromJson(resp, 200, ReviewResult.class);
    assertThat(result.labels).isNull();

    // Verify reviewer state.
    c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, admin, user);
    assertReviewers(c, CC);
    label = c.labels.get("Code-Review");
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(2);
    approvals.clear();
    for (ApprovalInfo approval : label.all) {
      approvals.put(approval._accountId, approval.value);
    }
    assertThat(approvals).containsEntry(admin.getId().get(), 0);
    assertThat(approvals).containsEntry(user.getId().get(), 0);
  }

  @Test
  public void reviewAndAddReviewers() throws Exception {
    TestAccount observer = accountCreator.user2();
    PushOneCommit.Result r = createChange();
    ReviewInput input =
        ReviewInput.approve().reviewer(user.email).reviewer(observer.email, CC, false);

    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    // Verify reviewer and CC were added. If not in NoteDb read mode, both
    // parties will be returned as CCed.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER, admin, user);
      assertReviewers(c, CC, observer);
    } else {
      // In legacy mode, everyone should be a reviewer.
      assertReviewers(c, REVIEWER, admin, user, observer);
      assertReviewers(c, CC);
    }

    // Verify emails were sent to added reviewers.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(2);

    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress, observer.emailAddress);
    assertThat(m.body()).contains(admin.fullName + " has posted comments on this change.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+2");

    m = messages.get(1);
    assertThat(m.rcpt()).containsExactly(user.emailAddress, observer.emailAddress);
    assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
  }

  @Test
  public void reviewAndAddGroupReviewers() throws Exception {
    int largeGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users = createAccounts(largeGroupSize, "reviewAndAddGroupReviewers");
    List<String> usernames = new ArrayList<>(largeGroupSize);
    for (TestAccount u : users) {
      usernames.add(u.username);
    }

    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");
    gApi.groups().id(largeGroup).addMembers(usernames.toArray(new String[largeGroupSize]));
    gApi.groups()
        .id(mediumGroup)
        .addMembers(usernames.subList(0, mediumGroupSize).toArray(new String[mediumGroupSize]));

    TestAccount observer = accountCreator.user2();
    PushOneCommit.Result r = createChange();

    // Attempt to add overly large group as reviewers.
    ReviewInput input =
        ReviewInput.approve()
            .reviewer(user.email)
            .reviewer(observer.email, CC, false)
            .reviewer(largeGroup);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input, SC_BAD_REQUEST);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(3);
    AddReviewerResult reviewerResult = result.reviewers.get(largeGroup);
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isNull();
    assertThat(reviewerResult.error).isNotNull();
    assertThat(reviewerResult.error).contains("has too many members to add them all as reviewers");

    // No labels should have changed, and no reviewers/CCs should have been added.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.messages).hasSize(1);
    assertThat(c.reviewers.get(REVIEWER)).isNull();
    assertThat(c.reviewers.get(CC)).isNull();

    // Attempt to add group large enough to require confirmation, without
    // confirmation, as reviewers.
    input =
        ReviewInput.approve()
            .reviewer(user.email)
            .reviewer(observer.email, CC, false)
            .reviewer(mediumGroup);
    result = review(r.getChangeId(), r.getCommit().name(), input, SC_BAD_REQUEST);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(3);
    reviewerResult = result.reviewers.get(mediumGroup);
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isTrue();
    assertThat(reviewerResult.error)
        .contains("has " + mediumGroupSize + " members. Do you want to add them all as reviewers?");

    // No labels should have changed, and no reviewers/CCs should have been added.
    c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.messages).hasSize(1);
    assertThat(c.reviewers.get(REVIEWER)).isNull();
    assertThat(c.reviewers.get(CC)).isNull();

    // Retrying with confirmation should successfully approve and add reviewers/CCs.
    input = ReviewInput.approve().reviewer(user.email).reviewer(mediumGroup, CC, true);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.messages).hasSize(2);

    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER, admin, user);
      assertReviewers(c, CC, users.subList(0, mediumGroupSize));
    } else {
      // If not in NoteDb mode, then everyone is a REVIEWER.
      List<TestAccount> expected = users.subList(0, mediumGroupSize);
      expected.add(admin);
      expected.add(user);
      assertReviewers(c, REVIEWER, expected);
      assertReviewers(c, CC);
    }
  }

  @Test
  public void noteDbAddReviewerToReviewerChangeInfo() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    in.state = CC;
    addReviewer(changeId, in);

    in.state = REVIEWER;
    addReviewer(changeId, in);

    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    setApiUser(user);
    // NoteDb adds reviewer to a change on every review.
    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    deleteReviewer(changeId, user).assertNoContent();

    ChangeInfo c = gApi.changes().id(changeId).get();
    assertThat(c.reviewerUpdates).isNotNull();
    assertThat(c.reviewerUpdates).hasSize(3);

    Iterator<ReviewerUpdateInfo> it = c.reviewerUpdates.iterator();
    ReviewerUpdateInfo reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(CC);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.getId().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.getId().get());

    reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(REVIEWER);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.getId().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.getId().get());

    reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(REMOVED);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.getId().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.getId().get());
  }

  @Test
  public void addDuplicateReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(user.email).reviewer(user.email);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);
    AddReviewerResult reviewerResult = result.reviewers.get(user.email);
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isNull();
    assertThat(reviewerResult.error).isNull();
  }

  @Test
  public void addOverlappingGroups() throws Exception {
    String emailPrefix = "addOverlappingGroups-";
    TestAccount user1 =
        accountCreator.create(name("user1"), emailPrefix + "user1@example.com", "User1");
    TestAccount user2 =
        accountCreator.create(name("user2"), emailPrefix + "user2@example.com", "User2");
    TestAccount user3 =
        accountCreator.create(name("user3"), emailPrefix + "user3@example.com", "User3");
    String group1 = createGroup("group1");
    String group2 = createGroup("group2");
    gApi.groups().id(group1).addMembers(user1.username, user2.username);
    gApi.groups().id(group2).addMembers(user2.username, user3.username);

    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(group1).reviewer(group2);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);
    AddReviewerResult reviewerResult = result.reviewers.get(group1);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).hasSize(2);
    reviewerResult = result.reviewers.get(group2);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).hasSize(1);

    // Repeat the above for CCs
    if (!notesMigration.readChanges()) {
      return;
    }
    r = createChange();
    input = ReviewInput.approve().reviewer(group1, CC, false).reviewer(group2, CC, false);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);
    reviewerResult = result.reviewers.get(group1);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.ccs).hasSize(2);
    reviewerResult = result.reviewers.get(group2);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.ccs).hasSize(1);

    // Repeat again with one group REVIEWER, the other CC. The overlapping
    // member should end up as a REVIEWER.
    r = createChange();
    input = ReviewInput.approve().reviewer(group1, REVIEWER, false).reviewer(group2, CC, false);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);
    reviewerResult = result.reviewers.get(group1);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).hasSize(2);
    reviewerResult = result.reviewers.get(group2);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).isNull();
    assertThat(reviewerResult.ccs).hasSize(1);
  }

  @Test
  public void removingReviewerRemovesTheirVote() throws Exception {
    String crLabel = "Code-Review";
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(admin.email);
    ReviewResult addResult = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(addResult.reviewers).isNotNull();
    assertThat(addResult.reviewers).hasSize(1);

    Map<String, LabelInfo> changeLabels = getChangeLabels(r.getChangeId());
    assertThat(changeLabels.get(crLabel).all).hasSize(1);

    RestResponse deleteResult = deleteReviewer(r.getChangeId(), admin);
    deleteResult.assertNoContent();

    changeLabels = getChangeLabels(r.getChangeId());
    assertThat(changeLabels.get(crLabel).all).isNull();

    // Check that the vote is gone even after the reviewer is added back
    addReviewer(r.getChangeId(), admin.email);
    changeLabels = getChangeLabels(r.getChangeId());
    assertThat(changeLabels.get(crLabel).all).isNull();
  }

  @Test
  public void notifyDetailsWorkOnPostReview() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount userToNotify = createAccounts(1, "notify-details-post-review").get(0);

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.reviewer(user.email, ReviewerState.REVIEWER, true);
    reviewInput.notify = NotifyHandling.NONE;
    reviewInput.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email)));

    sender.clear();
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.emailAddress);
  }

  @Test
  public void notifyDetailsWorkOnPostReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount userToNotify = createAccounts(1, "notify-details-post-reviewers").get(0);

    AddReviewerInput addReviewer = new AddReviewerInput();
    addReviewer.reviewer = user.email;
    addReviewer.notify = NotifyHandling.NONE;
    addReviewer.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email)));

    sender.clear();
    gApi.changes().id(r.getChangeId()).addReviewer(addReviewer);
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.emailAddress);
  }

  @Test
  public void removeReviewerWithVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    setApiUser(user);
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Code-Review", 1));
    setApiUser(newUser);
    exception.expect(AuthException.class);
    exception.expectMessage("remove reviewer not permitted");
    gApi.changes().id(r.getChangeId()).reviewer(user.email).remove();
  }

  @Test
  @Sandboxed
  public void removeReviewerWithoutVoteWithPermissionSucceeds() throws Exception {
    PushOneCommit.Result r = createChange();
    // This test creates a new user so that it can explicitly check the REMOVE_REVIEWER permission
    // rather than bypassing the check because of project or ref ownership.
    TestAccount newUser = createAccounts(1, name("foo")).get(0);
    grant(project, RefNames.REFS + "*", Permission.REMOVE_REVIEWER, false, REGISTERED_USERS);

    gApi.changes().id(r.getChangeId()).addReviewer(user.email);
    assertThatUserIsOnlyReviewer(r.getChangeId());
    setApiUser(newUser);
    gApi.changes().id(r.getChangeId()).reviewer(user.email).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get().reviewers).isEmpty();
  }

  @Test
  public void removeReviewerWithoutVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    gApi.changes().id(r.getChangeId()).addReviewer(user.email);
    setApiUser(newUser);
    exception.expect(AuthException.class);
    exception.expectMessage("remove reviewer not permitted");
    gApi.changes().id(r.getChangeId()).reviewer(user.email).remove();
  }

  @Test
  public void removeCCWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = user.email;
    input.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(input);
    setApiUser(newUser);
    exception.expect(AuthException.class);
    exception.expectMessage("remove reviewer not permitted");
    gApi.changes().id(r.getChangeId()).reviewer(user.email).remove();
  }

  private void assertThatUserIsOnlyReviewer(String changeId) throws Exception {
    AccountInfo userInfo = new AccountInfo(user.fullName, user.emailAddress.getEmail());
    userInfo._accountId = user.id.get();
    userInfo.username = user.username;
    assertThat(gApi.changes().id(changeId).get().reviewers)
        .containsExactly(ReviewerState.REVIEWER, ImmutableList.of(userInfo));
  }

  private AddReviewerResult addReviewer(String changeId, String reviewer) throws Exception {
    return addReviewer(changeId, reviewer, SC_OK);
  }

  private AddReviewerResult addReviewer(String changeId, String reviewer, int expectedStatus)
      throws Exception {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(changeId, in, expectedStatus);
  }

  private AddReviewerResult addReviewer(String changeId, AddReviewerInput in) throws Exception {
    return addReviewer(changeId, in, SC_OK);
  }

  private AddReviewerResult addReviewer(String changeId, AddReviewerInput in, int expectedStatus)
      throws Exception {
    RestResponse resp = adminRestSession.post("/changes/" + changeId + "/reviewers", in);
    return readContentFromJson(resp, expectedStatus, AddReviewerResult.class);
  }

  private RestResponse deleteReviewer(String changeId, TestAccount account) throws Exception {
    return adminRestSession.delete("/changes/" + changeId + "/reviewers/" + account.getId().get());
  }

  private ReviewResult review(String changeId, String revisionId, ReviewInput in) throws Exception {
    return review(changeId, revisionId, in, SC_OK);
  }

  private ReviewResult review(
      String changeId, String revisionId, ReviewInput in, int expectedStatus) throws Exception {
    RestResponse resp =
        adminRestSession.post("/changes/" + changeId + "/revisions/" + revisionId + "/review", in);
    return readContentFromJson(resp, expectedStatus, ReviewResult.class);
  }

  private static <T> T readContentFromJson(RestResponse r, int expectedStatus, Class<T> clazz)
      throws Exception {
    r.assertStatus(expectedStatus);
    try (JsonReader jsonReader = new JsonReader(r.getReader())) {
      jsonReader.setLenient(true);
      return newGson().fromJson(jsonReader, clazz);
    }
  }

  private static void assertReviewers(
      ChangeInfo c, ReviewerState reviewerState, TestAccount... accounts) throws Exception {
    List<TestAccount> accountList = new ArrayList<>(accounts.length);
    for (TestAccount a : accounts) {
      accountList.add(a);
    }
    assertReviewers(c, reviewerState, accountList);
  }

  private static void assertReviewers(
      ChangeInfo c, ReviewerState reviewerState, Iterable<TestAccount> accounts) throws Exception {
    Collection<AccountInfo> actualAccounts = c.reviewers.get(reviewerState);
    if (actualAccounts == null) {
      assertThat(accounts.iterator().hasNext()).isFalse();
      return;
    }
    assertThat(actualAccounts).isNotNull();
    List<Integer> actualAccountIds = new ArrayList<>(actualAccounts.size());
    for (AccountInfo account : actualAccounts) {
      actualAccountIds.add(account._accountId);
    }
    List<Integer> expectedAccountIds = new ArrayList<>();
    for (TestAccount account : accounts) {
      expectedAccountIds.add(account.getId().get());
    }
    assertThat(actualAccountIds).containsExactlyElementsIn(expectedAccountIds);
  }

  private List<TestAccount> createAccounts(int n, String emailPrefix) throws Exception {
    List<TestAccount> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(
          accountCreator.create(
              name("u" + i), emailPrefix + "-" + i + "@example.com", "Full Name " + i));
    }
    return result;
  }

  private Map<String, LabelInfo> getChangeLabels(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS).labels;
  }
}
