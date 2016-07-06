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
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gson.stream.JsonReader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(ConfigSuite.class)
public class ChangeReviewersIT extends AbstractDaemonTest {
  @Test
  public void addGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");
    TestAccount[] users = new TestAccount[PostReviewers.DEFAULT_MAX_REVIEWERS + 1];
    String[] largeGroupUsernames = new String[PostReviewers.DEFAULT_MAX_REVIEWERS + 1];
    String[] mediumGroupUsernames =
        new String[PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1];
    for (int i = 0; i < users.length; i++) {
      users[i] = accounts.create("u" + i, "u" + i + "@example.com", "Full Name " + i);
      largeGroupUsernames[i] = users[i].username;
      if (i < mediumGroupUsernames.length) {
        mediumGroupUsernames[i] = users[i].username;
      }
    }
    gApi.groups().id(largeGroup).addMembers(largeGroupUsernames);
    gApi.groups().id(mediumGroup).addMembers(mediumGroupUsernames);

    // Attempt to add overly large group as reviewers.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerResult result = addReviewer(changeId, largeGroup);
    assertThat(result.reviewer).isEqualTo(largeGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).contains("has too many members to add them all as reviewers");
    assertThat(result.reviewers).isNull();

    // Attempt to add medium group without confirmation.

    result = addReviewer(changeId, mediumGroup);
    assertThat(result.reviewer).isEqualTo(mediumGroup);
    assertThat(result.confirm).isTrue();
    assertThat(result.error)
        .contains("has " + mediumGroupUsernames.length + " members. Do you want to add them all"
            + " as reviewers?");
    assertThat(result.reviewers).isNull();

    // Add medium group with confirmation.
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = mediumGroup;
    in.confirmed = true;
    result = addReviewer(changeId, in);
    assertThat(result.reviewer).isEqualTo(mediumGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).hasSize(mediumGroupUsernames.length);

    // Verify that group members were added as reviewers.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, NoteDbMode.readWrite() ? REVIEWER : CC,
        Arrays.copyOf(users, mediumGroupUsernames.length));
  }

  @Test
  public void addCC() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    sender.clear();
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    // CC an individual account.
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    in.cc = true;
    AddReviewerResult result = addReviewer(changeId, in);
    assertThat(result.reviewer).isEqualTo(user.email);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    if (NoteDbMode.readWrite()) {
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
      assertReviewers(c, CC, user);
    }

    // Verify email was sent to CCed account.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    if (NoteDbMode.readWrite()) {
      assertThat(m.body()).contains(admin.fullName + " has uploaded a new change for review.");
    } else {
      assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
      assertThat(m.body()).contains("I'd like you to do a code review.");
    }

    // CC a group.
    TestAccount[] users = new TestAccount[3];
    TestAccount[] expectedUsers = new TestAccount[users.length + 1];
    expectedUsers[0] = user;
    String[] usernames = new String[users.length];
    for (int i = 0; i < users.length; i++) {
      users[i] = accounts.create("u" + i, "u" + i + "@example.com", "Full Name " + i);
      expectedUsers[i + 1] = users[i];
      usernames[i] = users[i].username;
    }
    in.reviewer = createGroup("cc");
    sender.clear();
    gApi.groups().id(in.reviewer).addMembers(usernames);
    result = addReviewer(changeId, in);
    assertThat(result.reviewer).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    if (NoteDbMode.readWrite()) {
      assertThat(result.reviewers).isNull();
    } else {
      assertThat(result.ccs).isNull();
    }
    c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, CC, expectedUsers);

    // Verify emails were sent to each of the group's accounts.
    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    Address[] expectedAddresses = new Address[users.length];
    for (int i = 0; i < users.length; i++) {
      expectedAddresses[i] = users[i].emailAddress;
    }
    assertThat(m.rcpt()).containsExactly((Object[]) expectedAddresses);

    // CC a group that overlaps with some existing reviewers and CCed accounts.
    TestAccount reviewer = accounts.create("reviewer", "reviewer@example.com", "Reviewer");
    result = addReviewer(changeId, reviewer.username);
    TestAccount[] moreUsers = new TestAccount[3];
    for (int i = 0; i < moreUsers.length; i++) {
      int x = users.length + i;
      moreUsers[i] = accounts.create("u" + x, "u" + x + "@example.com", "Full Name " + x);
    }
    users = Arrays.copyOf(users, users.length + moreUsers.length);
    usernames = Arrays.copyOf(usernames, users.length);
    for (int i = 0; i < moreUsers.length; i++) {
      int j = users.length - moreUsers.length + i;
      users[j] = moreUsers[i];
      usernames[j] = moreUsers[i].username;
    }
    gApi.groups().id(in.reviewer).addMembers(usernames);
    gApi.groups().id(in.reviewer).addMembers(reviewer.username);
    sender.clear();
    result = addReviewer(changeId, in);
    assertThat(result.reviewer).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    c = gApi.changes().id(r.getChangeId()).get();
    if (NoteDbMode.readWrite()) {
      assertThat(result.ccs).hasSize(moreUsers.length);
      assertThat(result.reviewers).isNull();
      expectedUsers = Arrays.copyOf(users, users.length + 1);
      expectedUsers[users.length] = user;
      assertReviewers(c, REVIEWER, reviewer);
      assertReviewers(c, CC, expectedUsers);
    } else {
      assertThat(result.ccs).isNull();
      assertThat(result.reviewers).hasSize(moreUsers.length);
      expectedUsers = Arrays.copyOf(users, users.length + 2);
      expectedUsers[users.length] = user;
      expectedUsers[users.length + 1] = reviewer;
      assertReviewers(c, CC, expectedUsers);
    }

    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    expectedAddresses = new Address[moreUsers.length + 1];
    for (int i = 0; i < moreUsers.length; i++) {
      expectedAddresses[i] = moreUsers[i].emailAddress;
    }
    if (NoteDbMode.readWrite()) {
      expectedAddresses[expectedAddresses.length - 1] = reviewer.emailAddress;
    } else {
      expectedAddresses = Arrays.copyOf(expectedAddresses, expectedAddresses.length - 1);
    }
    assertThat(m.rcpt()).containsExactly((Object[]) expectedAddresses);
  }

  @Test
  public void reviewAndAddReviewers() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    sender.clear();
    TestAccount observer = accounts.user2();
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve()
        .reviewer(user.email)
        .reviewer(observer.email, true, false);

    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    // Verify reviewer and CC were added. If not in NoteDb read mode, both
    // parties will be returned as CCed.
    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();
    if (NoteDbMode.readWrite()) {
      assertReviewers(c, REVIEWER, admin, user);
      assertReviewers(c, CC, observer);
    } else {
      // In legacy mode, change owner should be the only reviewer.
      assertReviewers(c, REVIEWER, admin);
      assertReviewers(c, CC, user, observer);
   }

    // Verify emails were sent to added reviewers.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(3);
    // First email to user.
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    // Second email to reviewer and observer.
    m = messages.get(1);
    if (NoteDbMode.readWrite()) {
      assertThat(m.rcpt()).containsExactly(user.emailAddress, observer.emailAddress);
      assertThat(m.body()).contains(admin.fullName + " has uploaded a new change for review.");
    } else {
      assertThat(m.rcpt()).containsExactly(observer.emailAddress);
      assertThat(m.body()).contains("Hello " + observer.fullName + ",\n");
      assertThat(m.body()).contains("I'd like you to do a code review.");
    }

    // Third email is review to user and observer.
    m = messages.get(2);
    assertThat(m.rcpt()).containsExactly(user.emailAddress, observer.emailAddress);
    assertThat(m.body()).contains(admin.fullName + " has posted comments on this change.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+2\n");
  }

  @Test
  public void reviewAndAddGroupReviewers() throws Exception {
    TestAccount[] users = new TestAccount[PostReviewers.DEFAULT_MAX_REVIEWERS + 1];
    String[] largeGroupUsernames = new String[PostReviewers.DEFAULT_MAX_REVIEWERS + 1];
    String[] mediumGroupUsernames =
        new String[PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1];
    for (int i = 0; i < users.length; i++) {
      users[i] = accounts.create("u" + i, "u" + i + "@example.com", "Full Name " + i);
      largeGroupUsernames[i] = users[i].username;
      if (i < mediumGroupUsernames.length) {
        mediumGroupUsernames[i] = users[i].username;
      }
    }

    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");
    gApi.groups().id(largeGroup).addMembers(largeGroupUsernames);
    gApi.groups().id(mediumGroup).addMembers(mediumGroupUsernames);

    TestAccount observer = accounts.user2();
    PushOneCommit.Result r = createChange();

    // Attempt to add overly large group as reviewers.
    ReviewInput input = ReviewInput.approve()
        .reviewer(user.email)
        .reviewer(observer.email, true, false)
        .reviewer(largeGroup);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(3);
    AddReviewerResult reviewerResult = result.reviewers.get(largeGroup);
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isNull();
    assertThat(reviewerResult.error).isNotNull();
    assertThat(reviewerResult.error).contains("has too many members to add them all as reviewers");

    // No labels should have changed, and no reviewers/CCs should have been added.
    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();
    assertThat(c.messages).hasSize(1);
    assertThat(c.reviewers.get(REVIEWER)).isNull();
    assertThat(c.reviewers.get(CC)).isNull();

    // Attempt to add group large enough to require confirmation, without
    // confirmation, as reviewers.
    input = ReviewInput.approve()
        .reviewer(user.email)
        .reviewer(observer.email, true, false)
        .reviewer(mediumGroup);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(3);
    reviewerResult = result.reviewers.get(mediumGroup);
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isTrue();
    assertThat(reviewerResult.error)
        .contains("has " + mediumGroupUsernames.length + " members. Do you want to add them all"
            + " as reviewers?");

    // No labels should have changed, and no reviewers/CCs should have been added.
    c = gApi.changes()
        .id(r.getChangeId())
        .get();
    assertThat(c.messages).hasSize(1);
    assertThat(c.reviewers.get(REVIEWER)).isNull();
    assertThat(c.reviewers.get(CC)).isNull();

    // Retrying with confirmation should successfully approve and add reviewers/CCs.
    input = ReviewInput.approve()
        .reviewer(user.email)
        .reviewer(mediumGroup, true, true);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    c = gApi.changes()
        .id(r.getChangeId())
        .get();
    assertThat(c.messages).hasSize(2);

    if (NoteDbMode.readWrite()) {
      assertReviewers(c, REVIEWER, admin, user);
      TestAccount[] expectedCC = Arrays.copyOf(users, mediumGroupUsernames.length);
      assertReviewers(c, CC, expectedCC);
    } else {
      // If not in NoteDb mode, then user is returned with the CC group.
      assertReviewers(c, REVIEWER, admin);
      TestAccount[] expectedCC = new TestAccount[mediumGroupUsernames.length + 1];
      expectedCC[0] = user;
      for (int i = 1; i < expectedCC.length; i++) {
        expectedCC[i] = users[i - 1];
      }
      assertReviewers(c, CC, expectedCC);
    }
  }

  AddReviewerResult addReviewer(String changeId, String reviewer) throws Exception {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(changeId, in);
  }

  AddReviewerResult addReviewer(String changeId, AddReviewerInput in) throws Exception {
    RestResponse resp = adminRestSession.post("/changes/" + changeId + "/reviewers", in);
    return readContentFromJson(resp, AddReviewerResult.class);
  }

  ReviewResult review(String changeId, String revisionId, ReviewInput in) throws Exception {
    RestResponse resp = adminRestSession.post(
        "/changes/" + changeId + "/revisions/" + revisionId + "/review", in);
    return readContentFromJson(resp, ReviewResult.class);
  }

  private static <T> T readContentFromJson(RestResponse r, Class<T> clazz) throws Exception {
    r.assertOK();
    JsonReader jsonReader = new JsonReader(r.getReader());
    jsonReader.setLenient(true);
    return newGson().fromJson(jsonReader, clazz);
  }

  private static void assertReviewers(ChangeInfo c, ReviewerState reviewerState,
      TestAccount... accounts) throws Exception {
    Collection<AccountInfo> actualAccounts = c.reviewers.get(reviewerState);
    assertThat(actualAccounts).isNotNull();
    List<Integer> actualAccountIds = Lists.newArrayListWithCapacity(actualAccounts.size());
    for (AccountInfo account : actualAccounts) {
      actualAccountIds.add(account._accountId);
    }
    List<Integer> expectedAccountIds = Lists.newArrayListWithCapacity(accounts.length);
    for (TestAccount account : accounts) {
      expectedAccountIds.add(account.getId().get());
    }
    assertThat(actualAccountIds).containsExactlyElementsIn(expectedAccountIds);
  }
}
