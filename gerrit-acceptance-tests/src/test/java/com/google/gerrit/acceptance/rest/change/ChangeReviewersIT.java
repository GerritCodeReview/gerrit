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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gson.stream.JsonReader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ChangeReviewersIT extends AbstractDaemonTest {
  @Test
  public void addGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");

    int largeGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users =
        createAccounts(largeGroupSize, "addGroupAsReviewer");
    List<String> largeGroupUsernames = new ArrayList<>(mediumGroupSize);
    for (TestAccount u : users) {
      largeGroupUsernames.add(u.username);
    }
    List<String> mediumGroupUsernames =
        largeGroupUsernames.subList(0, mediumGroupSize);
    gApi.groups().id(largeGroup).addMembers(
        largeGroupUsernames.toArray(new String[largeGroupSize]));
    gApi.groups().id(mediumGroup).addMembers(
        mediumGroupUsernames.toArray(new String[mediumGroupSize]));

    // Attempt to add overly large group as reviewers.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerResult result = addReviewer(changeId, largeGroup);
    assertThat(result.input).isEqualTo(largeGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error)
        .contains("has too many members to add them all as reviewers");
    assertThat(result.reviewers).isNull();

    // Attempt to add medium group without confirmation.
    result = addReviewer(changeId, mediumGroup);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isTrue();
    assertThat(result.error)
        .contains("has " + mediumGroupSize + " members. Do you want to add them"
            + " all as reviewers?");
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
    assertReviewers(c, notesMigration.readChanges() ? REVIEWER : CC,
        users.subList(0, mediumGroupSize));
  }

  // TODO(logan): Add tests for CC->REVIEWER and REVIEWER->CC state transitions.

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
      assertReviewers(c, CC, user);
    }

    // Verify email was sent to CCed account.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    if (notesMigration.readChanges()) {
      assertThat(m.body())
          .contains(admin.fullName + " has uploaded a new change for review.");
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
    gApi.groups().id(in.reviewer)
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
    assertReviewers(c, CC, firstUsers);

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
    TestAccount reviewer = accounts.create(name("reviewer"),
        "addCcGroup-reviewer@example.com", "Reviewer");
    result = addReviewer(changeId, reviewer.username);
    assertThat(result.error).isNull();
    sender.clear();
    in.reviewer = createGroup("cc2");
    gApi.groups().id(in.reviewer)
        .addMembers(usernames.toArray(new String[usernames.size()]));
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
      assertReviewers(c, CC, expectedUsers);
    }

    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    expectedAddresses = new ArrayList<>(4);
    for (int i = 0; i < 3; i++) {
      expectedAddresses.add(users.get(users.size() - i - 1).emailAddress);
    }
    if (notesMigration.readChanges()) {
      expectedAddresses.add(reviewer.emailAddress);
    }
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
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC, user);

    in.state = REVIEWER;
    addReviewer(changeId, in);
    c = gApi.changes().id(r.getChangeId()).get();
    if (notesMigration.readChanges()) {
      assertReviewers(c, REVIEWER, user);
      assertReviewers(c, CC);
    } else {
      // If NoteDb not enabled, should have had no effect.
      assertReviewers(c, REVIEWER);
      assertReviewers(c, CC, user);
    }
  }

  private AddReviewerResult addReviewer(String changeId, String reviewer)
      throws Exception {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(changeId, in);
  }

  private AddReviewerResult addReviewer(String changeId, AddReviewerInput in)
      throws Exception {
    RestResponse resp =
        adminRestSession.post("/changes/" + changeId + "/reviewers", in);
    return readContentFromJson(resp, AddReviewerResult.class);
  }

  private static <T> T readContentFromJson(RestResponse r, Class<T> clazz)
      throws Exception {
    r.assertOK();
    JsonReader jsonReader = new JsonReader(r.getReader());
    jsonReader.setLenient(true);
    return newGson().fromJson(jsonReader, clazz);
  }

  private static void assertReviewers(ChangeInfo c, ReviewerState reviewerState,
      TestAccount... accounts) throws Exception {
    List<TestAccount> accountList = new ArrayList<>(accounts.length);
    for (TestAccount a : accounts) {
      accountList.add(a);
    }
    assertReviewers(c, reviewerState, accountList);
  }

  private static void assertReviewers(ChangeInfo c, ReviewerState reviewerState,
      Iterable<TestAccount> accounts) throws Exception {
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

  private List<TestAccount> createAccounts(int n, String emailPrefix)
      throws Exception {
    List<TestAccount> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(accounts.create(name("u" + i),
          emailPrefix + "-" + i + "@example.com", "Full Name " + i));
    }
    return result;
  }
}