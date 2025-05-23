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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REMOVED;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.extensions.common.testing.ChangeInfoSubject.assertThat;
import static com.google.gerrit.extensions.common.testing.ChangeInfoSubject.vote;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.truth.Correspondence;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewerResult;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ReviewerUpdateInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.account.ListGroupMembership;
import com.google.gerrit.server.change.ReviewerModifier;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.group.testing.TestGroupBackend;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.truth.NullAwareCorrespondence;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ChangeReviewersIT extends AbstractDaemonTest {

  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void addInternalGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = groupOperations.newGroup().name("largeGroup").create().get();
    String mediumGroup = groupOperations.newGroup().name("mediumGroup").create().get();

    int largeGroupSize = ReviewerModifier.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = ReviewerModifier.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users = createAccounts(largeGroupSize, "addGroupAsReviewer");
    List<String> largeGroupUsernames = new ArrayList<>(mediumGroupSize);
    for (TestAccount u : users) {
      largeGroupUsernames.add(u.username());
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
    ReviewerResult result = addReviewer(changeId, largeGroup, SC_BAD_REQUEST);
    assertThat(result.input).isEqualTo(largeGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).contains("has too many members to add them all as reviewers");
    assertThat(result.reviewers).isNull();

    // Attempt to add medium group without confirmation.
    result = addReviewer(changeId, mediumGroup, SC_BAD_REQUEST);
    List<TestAccount> mediumGroupMembers = users.subList(0, mediumGroupSize);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isTrue();
    assertThat(result.error)
        .contains("has " + mediumGroupSize + " members. Do you want to add them all as reviewers?");
    assertThat(result.reviewers).isNull();

    // Add medium group with confirmation.
    ReviewerInput in = new ReviewerInput();
    in.reviewer = mediumGroup;
    in.confirmed = true;
    result = addReviewer(changeId, in);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers)
        .comparingElementsUsing(hasAccountId())
        .containsExactlyElementsIn(toAccountIds(mediumGroupMembers));

    // Verify that group members were added as reviewers.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, mediumGroupMembers);
  }

  @Test
  public void addExternalGroupAsReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    TestGroupBackend testGroupBackend = new TestGroupBackend();
    GroupDescription.Basic externalGroup = testGroupBackend.create("External Group");
    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(testGroupBackend)) {
      ReviewerInput in = new ReviewerInput();
      in.reviewer = externalGroup.getGroupUUID().get();
      in.confirmed = true;
      ReviewerResult result = addReviewer(changeId, in, SC_BAD_REQUEST);
      assertThat(result.error)
          .isEqualTo(
              String.format("The group %s cannot be added as reviewer.", externalGroup.getName()));
    }
  }

  @Test
  public void addSystemGroupAsReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    ReviewerInput in = new ReviewerInput();
    in.reviewer = SystemGroupBackend.REGISTERED_USERS.get();
    in.confirmed = true;
    ReviewerResult result = addReviewer(changeId, in, SC_BAD_REQUEST);
    assertThat(result.error).isEqualTo("The group Registered Users cannot be added as reviewer.");
  }

  @Test
  public void addProjectOwnersAsReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    List<TestAccount> internalProjectOwners = createAccounts(3, "project-owner");
    AccountGroup.UUID ownerGroup1 =
        groupOperations
            .newGroup()
            .addMember(internalProjectOwners.get(0).id())
            .addMember(internalProjectOwners.get(1).id())
            .create();
    AccountGroup.UUID ownerGroup2 =
        groupOperations.newGroup().addMember(internalProjectOwners.get(2).id()).create();

    TestGroupBackend testGroupBackend = new TestGroupBackend();
    GroupDescription.Basic externalOwnersGroup = testGroupBackend.create("External Group");
    TestAccount externalProjectOwner = createAccount("external-project-owner");
    testGroupBackend.setMembershipsOf(
        externalProjectOwner.id(),
        new ListGroupMembership(ImmutableList.of(externalOwnersGroup.getGroupUUID())));
    try (ExtensionRegistry.Registration registration =
        extensionRegistry.newRegistration().add(testGroupBackend)) {
      projectOperations
          .project(project)
          .forUpdate()
          .add(allow(Permission.OWNER).ref(RefNames.REFS + "*").group(ownerGroup1))
          .add(allow(Permission.OWNER).ref(RefNames.REFS + "*").group(ownerGroup2))
          .add(
              allow(Permission.OWNER)
                  .ref(RefNames.REFS + "*")
                  .group(externalOwnersGroup.getGroupUUID()))
          .update();

      ReviewerInput in = new ReviewerInput();
      in.reviewer = "global:Project-Owners";
      in.confirmed = true;
      ReviewerResult result = addReviewer(changeId, in);

      // only users that are members of internal groups to which the OWNER permission is assigned
      // are added as reviewers, members of external owner groups and admins (which are implicitly
      // owning all projects) are omitted.

      assertThat(result.input).isEqualTo(in.reviewer);
      assertThat(result.confirm).isNull();
      assertThat(result.error).isNull();
      assertThat(result.reviewers)
          .comparingElementsUsing(hasAccountId())
          .containsExactlyElementsIn(
              internalProjectOwners.stream().map(TestAccount::id).collect(toImmutableSet()));

      // Verify that internal group members were added as reviewers.
      ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
      assertReviewers(c, REVIEWER, internalProjectOwners);
    }
  }

  @Test
  public void addCcAccount() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ReviewerInput in = new ReviewerInput();
    in.reviewer = user.email();
    in.state = CC;
    ReviewerResult result = addReviewer(changeId, in);

    assertThat(result.input).isEqualTo(user.email());
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertThat(result.reviewers).isNull();
    assertThat(result.ccs).hasSize(1);
    AccountInfo ai = result.ccs.get(0);
    assertThat(ai._accountId).isEqualTo(user.id().get());
    assertReviewers(c, CC, user);

    // Verify email was sent to CCed account.
    ImmutableList<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getNameEmail());
    assertThat(m.body()).contains(admin.fullName() + " has uploaded this change for review.");
  }

  @Test
  public void addCcEmailWithoutAccount() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String testEmailAddress = "email@without.account";

    // Add a reviewer
    ReviewerInput ri = new ReviewerInput();
    ri.reviewer = user.email();
    ri.state = REVIEWER;
    ReviewerResult result = addReviewer(changeId, ri);
    assertThat(result.input).isEqualTo(user.email());
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).hasSize(1);

    // Add an email address that has no account to CC
    ReviewerInput ccInput = new ReviewerInput();
    ccInput.reviewer = testEmailAddress;
    ccInput.state = CC;
    ReviewerResult resultCC = addReviewer(changeId, ccInput, SC_BAD_REQUEST);
    assertThat(resultCC.error).contains("Account '" + testEmailAddress + "' not found");
    assertThat(resultCC.error)
        .contains(testEmailAddress + " does not identify a registered user or group");
  }

  @Test
  public void addReviewerEmailWithoutAccount() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String testEmailAddress = "email@without.account";

    // Add a reviewer without an account
    ReviewerInput ri = new ReviewerInput();
    ri.reviewer = testEmailAddress;
    ri.state = REVIEWER;
    ReviewerResult result = addReviewer(changeId, ri, SC_BAD_REQUEST);
    assertThat(result.error).contains("Account '" + testEmailAddress + "' not found");
    assertThat(result.error)
        .contains(testEmailAddress + " does not identify a registered user or group");
  }

  @Test
  public void addCcGroup() throws Exception {
    List<TestAccount> users = createAccounts(6, "addCcGroup");
    List<String> usernames = new ArrayList<>(6);
    for (TestAccount u : users) {
      usernames.add(u.username());
    }

    List<TestAccount> firstUsers = users.subList(0, 3);
    List<String> firstUsernames = usernames.subList(0, 3);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ReviewerInput in = new ReviewerInput();
    in.reviewer = groupOperations.newGroup().name("cc1").create().get();
    in.state = CC;
    gApi.groups()
        .id(in.reviewer)
        .addMembers(firstUsernames.toArray(new String[firstUsernames.size()]));
    ReviewerResult result = addReviewer(changeId, in);

    assertThat(result.input).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).isNull();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, CC, firstUsers);

    // Verify emails were sent to each of the group's accounts.
    ImmutableList<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    List<Address> expectedAddresses = new ArrayList<>(firstUsers.size());
    for (TestAccount u : firstUsers) {
      expectedAddresses.add(u.getNameEmail());
    }
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);

    // CC a group that overlaps with some existing reviewers and CCed accounts.
    TestAccount reviewer =
        accountCreator.create(
            name("reviewer"), "addCcGroup-reviewer@example.com", "Reviewer", null);
    result = addReviewer(changeId, reviewer.username());
    assertThat(result.error).isNull();
    sender.clear();
    in.reviewer = groupOperations.newGroup().name("cc2").create().get();
    gApi.groups().id(in.reviewer).addMembers(usernames.toArray(new String[usernames.size()]));
    gApi.groups().id(in.reviewer).addMembers(reviewer.username());
    result = addReviewer(changeId, in);
    assertThat(result.input).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    c = gApi.changes().id(r.getChangeId()).get();
    assertThat(result.ccs).hasSize(3);
    assertThat(result.reviewers).isNull();
    assertReviewers(c, REVIEWER, reviewer);
    assertReviewers(c, CC, users);

    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    expectedAddresses = new ArrayList<>(4);
    for (int i = 0; i < 3; i++) {
      expectedAddresses.add(users.get(users.size() - i - 1).getNameEmail());
    }
    // 'reviewer' is not included in the email, since it has already been notified.
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);
  }

  @Test
  public void transitionCcToReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ReviewerInput in = new ReviewerInput();
    in.reviewer = user.email();
    in.state = CC;
    addReviewer(changeId, in);
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC, user);

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
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC, user);
  }

  @Test
  public void addSelfAsReviewer() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // user adds self as REVIEWER.
    ReviewInput input = new ReviewInput().reviewer(user.username());
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
    LabelInfo label = c.labels.get(LabelId.CODE_REVIEW);
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(1);
    ApprovalInfo approval = label.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void addSelfAsCc() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // user adds self as CC.
    ReviewInput input = new ReviewInput().reviewer(user.username(), CC, false);
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
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC, user);
    // Verify no approvals were added.
    assertThat(c.labels).isNotNull();
    LabelInfo label = c.labels.get(LabelId.CODE_REVIEW);
    assertThat(label).isNotNull();
    assertThat(label.all).isNull();
  }

  @Test
  public void reviewerReplyWithoutVote() throws Exception {
    // Create change owned by admin.
    PushOneCommit.Result r = createChange();

    // Verify reviewer state.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER);
    assertReviewers(c, CC);
    LabelInfo label = c.labels.get(LabelId.CODE_REVIEW);
    assertThat(label).isNotNull();
    assertThat(label.all).isNull();

    // Add user as REVIEWER.
    ReviewInput input = new ReviewInput().reviewer(user.username());
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);

    // Verify reviewer state.
    c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, user);
    assertReviewers(c, CC);
    label = c.labels.get(LabelId.CODE_REVIEW);
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(1);
    Map<Integer, Integer> approvals = new HashMap<>();
    for (ApprovalInfo approval : label.all) {
      approvals.put(approval._accountId, approval.value);
    }
    assertThat(approvals).containsEntry(user.id().get(), 0);

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
    assertReviewers(c, REVIEWER, user);
    assertReviewers(c, CC);
    label = c.labels.get(LabelId.CODE_REVIEW);
    assertThat(label).isNotNull();
    assertThat(label.all).isNotNull();
    assertThat(label.all).hasSize(1);
    approvals.clear();
    for (ApprovalInfo approval : label.all) {
      approvals.put(approval._accountId, approval.value);
    }
    assertThat(approvals).containsEntry(user.id().get(), 0);
  }

  @Test
  public void reviewAndAddReviewers() throws Exception {
    TestAccount observer = accountCreator.user2();
    PushOneCommit.Result r = createChange();
    ReviewInput input =
        ReviewInput.approve().reviewer(user.email()).reviewer(observer.email(), CC, false);

    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    // Verify reviewer and CC were added.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, admin, user);
    assertReviewers(c, CC, observer);

    // Verify emails were sent to added reviewers.
    ImmutableList<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(2);

    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getNameEmail(), observer.getNameEmail());
    assertThat(m.body())
        .contains(
            admin.fullName() + " has posted comments on this change by " + admin.fullName() + ".");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+2");

    m = messages.get(1);
    assertThat(m.rcpt()).containsExactly(user.getNameEmail(), observer.getNameEmail());
    assertThat(m.body()).contains("Hello " + user.fullName() + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
  }

  @Test
  public void reviewAndAddGroupReviewers() throws Exception {
    int largeGroupSize = ReviewerModifier.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = ReviewerModifier.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users = createAccounts(largeGroupSize, "reviewAndAddGroupReviewers");
    List<String> usernames = new ArrayList<>(largeGroupSize);
    for (TestAccount u : users) {
      usernames.add(u.username());
    }

    String largeGroup = groupOperations.newGroup().name("largeGroup").create().get();
    String mediumGroup = groupOperations.newGroup().name("mediumGroup").create().get();
    gApi.groups().id(largeGroup).addMembers(usernames.toArray(new String[largeGroupSize]));
    gApi.groups()
        .id(mediumGroup)
        .addMembers(usernames.subList(0, mediumGroupSize).toArray(new String[mediumGroupSize]));

    TestAccount observer = accountCreator.user2();
    PushOneCommit.Result r = createChange();

    // Attempt to add overly large group as reviewers.
    ReviewInput input =
        ReviewInput.approve()
            .reviewer(user.email())
            .reviewer(observer.email(), CC, false)
            .reviewer(largeGroup);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input, SC_BAD_REQUEST);
    assertThat(result.labels).isNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(3);

    ReviewerResult reviewerResult = result.reviewers.get(largeGroup);
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
            .reviewer(user.email())
            .reviewer(observer.email(), CC, false)
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
    input = ReviewInput.approve().reviewer(user.email()).reviewer(mediumGroup, CC, true);
    result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.labels).isNotNull();
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.messages).hasSize(2);

    assertReviewers(c, REVIEWER, admin, user);
    assertReviewers(c, CC, users.subList(0, mediumGroupSize));
  }

  @Test
  public void addReviewerToReviewerUpdateInfo() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    ReviewerInput in = new ReviewerInput();
    in.reviewer = user.email();
    in.state = CC;
    addReviewer(changeId, in);

    in.state = REVIEWER;
    addReviewer(changeId, in);

    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    requestScopeOperations.setApiUser(user.id());
    // By posting a review the user is added as reviewer.
    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    deleteReviewer(changeId, user).assertNoContent();

    ChangeInfo c = gApi.changes().id(changeId).get();
    assertThat(c.reviewerUpdates).isNotNull();
    assertThat(c.reviewerUpdates).hasSize(3);

    Iterator<ReviewerUpdateInfo> it = c.reviewerUpdates.iterator();
    ReviewerUpdateInfo reviewerUpdateInfo = it.next();
    assertThat(reviewerUpdateInfo.state).isEqualTo(CC);
    assertThat(reviewerUpdateInfo.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerUpdateInfo.updatedBy._accountId).isEqualTo(admin.id().get());

    reviewerUpdateInfo = it.next();
    assertThat(reviewerUpdateInfo.state).isEqualTo(REVIEWER);
    assertThat(reviewerUpdateInfo.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerUpdateInfo.updatedBy._accountId).isEqualTo(admin.id().get());

    reviewerUpdateInfo = it.next();
    assertThat(reviewerUpdateInfo.state).isEqualTo(REMOVED);
    assertThat(reviewerUpdateInfo.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerUpdateInfo.updatedBy._accountId).isEqualTo(admin.id().get());
  }

  @Test
  public void addDuplicateReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(user.email()).reviewer(user.email());
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);

    ReviewerResult reviewerResult = result.reviewers.get(user.email());
    assertThat(reviewerResult).isNotNull();
    assertThat(reviewerResult.confirm).isNull();
    assertThat(reviewerResult.error).isNull();
  }

  @Test
  public void addOverlappingGroups() throws Exception {
    String emailPrefix = "addOverlappingGroups-";
    TestAccount user1 =
        accountCreator.create(name("user1"), emailPrefix + "user1@example.com", "User1", null);
    TestAccount user2 =
        accountCreator.create(name("user2"), emailPrefix + "user2@example.com", "User2", null);
    TestAccount user3 =
        accountCreator.create(name("user3"), emailPrefix + "user3@example.com", "User3", null);
    String group1 = groupOperations.newGroup().name("group1").create().get();
    String group2 = groupOperations.newGroup().name("group2").create().get();
    gApi.groups().id(group1).addMembers(user1.username(), user2.username());
    gApi.groups().id(group2).addMembers(user2.username(), user3.username());

    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(group1).reviewer(group2);
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(2);

    ReviewerResult reviewerResult = result.reviewers.get(group1);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).hasSize(2);
    reviewerResult = result.reviewers.get(group2);
    assertThat(reviewerResult.error).isNull();
    assertThat(reviewerResult.reviewers).hasSize(1);

    // Repeat the above for CCs
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
    String crLabel = LabelId.CODE_REVIEW;
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(admin.email());
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
    addReviewer(r.getChangeId(), admin.email());
    changeLabels = getChangeLabels(r.getChangeId());
    assertThat(changeLabels.get(crLabel).all).isNull();
  }

  @Test
  public void notifyDetailsWorkOnPostReview() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount userToNotify = createAccounts(1, "notify-details-post-review").get(0);

    ReviewInput reviewInput = new ReviewInput();
    reviewInput.reviewer(user.email(), ReviewerState.REVIEWER, true);
    reviewInput.notify = NotifyHandling.NONE;
    reviewInput.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email())));

    sender.clear();
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.getNameEmail());
  }

  @Test
  public void notifyDetailsWorkOnPostReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount userToNotify = createAccounts(1, "notify-details-post-reviewers").get(0);

    ReviewerInput addReviewer = new ReviewerInput();
    addReviewer.reviewer = user.email();
    addReviewer.notify = NotifyHandling.NONE;
    addReviewer.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email())));

    sender.clear();
    gApi.changes().id(r.getChangeId()).addReviewer(addReviewer);
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.getNameEmail());
  }

  @Test
  public void removeReviewerWithVoteOnMergedChangeForChangeOwnerFails() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));
    gApi.changes().id(r.getChangeId()).current().submit();

    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("cannot remove votes from merged change");
  }

  @Test
  public void removeReviewerWithVoteOnMergedChangeForUserFails() throws Exception {
    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));
    gApi.changes().id(r.getChangeId()).current().submit();

    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("cannot remove votes from merged change");
  }

  @Test
  public void removeReviewerWithoutVoteOnMergedChangeForChangeOwnerSucceeds() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));
    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(
            Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).get().removableReviewers)
                .email)
        .isEqualTo(user.email());

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();

    // Admin is a "reviewer" since the admin submitted the change, this ensures user is not a
    // reviewer.
    assertThat(
            Iterables.getOnlyElement(
                    gApi.changes().id(r.getChangeId()).get().reviewers.get(REVIEWER))
                .email)
        .doesNotMatch(user.email());
  }

  @Test
  public void removeReviewerWithoutVoteOnMergedChangeForUserSucceeds() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));
    gApi.changes().id(r.getChangeId()).current().submit();

    requestScopeOperations.setApiUser(user.id());
    assertThat(
            Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).get().removableReviewers)
                .email)
        .isEqualTo(user.email());

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();

    // Admin is a "reviewer" since the admin submitted the change, this ensures user is not a
    // reviewer.
    assertThat(
            Iterables.getOnlyElement(
                    gApi.changes().id(r.getChangeId()).get().reviewers.get(REVIEWER))
                .email)
        .doesNotMatch(user.email());
  }

  @Test
  public void removeReviewerWithVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("remove reviewer not permitted");
  }

  @Test
  public void removeReviewerWithVoteFromMergedChangeFailsWithRemoveReviewerPermission()
      throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(allow(Permission.REMOVE_REVIEWER).ref(RefNames.REFS + "*").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(r.getChangeId()).current().submit();

    TestAccount newUser = createAccounts(1, name("foo")).get(0);
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("cannot remove votes from merged change");
  }

  @Test
  public void removeSubmitterFromMergedChangeFailsWithRemoveReviewerPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(allow(Permission.REMOVE_REVIEWER).ref(RefNames.REFS + "*").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result r = createChange();

    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes().id(r.getChangeId()).current().submit();

    TestAccount newUser = createAccounts(1, name("foo")).get(0);
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(admin.email()).remove());
    assertThat(thrown).hasMessageThat().contains("cannot remove votes from merged change");
  }

  @Test
  @Sandboxed
  public void removeReviewerWithoutVoteFromOpenChangeWithPermissionSucceeds() throws Exception {
    PushOneCommit.Result r = createChange();
    // This test creates a new user so that it can explicitly check the REMOVE_REVIEWER permission
    // rather than bypassing the check because of project or ref ownership.
    TestAccount newUser = createAccounts(1, name("foo")).get(0);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.REMOVE_REVIEWER).ref(RefNames.REFS + "*").group(REGISTERED_USERS))
        .update();

    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    assertThatUserIsOnlyReviewer(r.getChangeId());
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(
            Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).get().removableReviewers)
                .email)
        .isEqualTo(user.email());

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get().reviewers).isEmpty();
  }

  @Test
  @Sandboxed
  public void removeReviewerWithoutVoteOnAMergedChangeWithPermissionSucceeds() throws Exception {
    PushOneCommit.Result r = createChange();
    // This test creates a new user so that it can explicitly check the REMOVE_REVIEWER permission
    // rather than bypassing the check because of project or ref ownership.
    TestAccount newUser = createAccounts(1, name("foo")).get(0);
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.REMOVE_REVIEWER).ref(RefNames.REFS + "*").group(REGISTERED_USERS))
        .update();

    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    requestScopeOperations.setApiUser(newUser.id());

    // Ensures user is removable.
    assertThat(
            gApi.changes().id(r.getChangeId()).get().removableReviewers.stream()
                .filter(a -> user.email().equals(a.email))
                .findAny()
                .isPresent())
        .isTrue();
    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();
  }

  @Test
  public void removeReviewerWithoutVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("remove reviewer not permitted");
  }

  @Test
  public void removeCCWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(input);
    requestScopeOperations.setApiUser(newUser.id());
    assertThat(gApi.changes().id(r.getChangeId()).get().removableReviewers).isEmpty();

    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("remove reviewer not permitted");
  }

  @Test
  public void removeReviewerWithVoteAndThenAddThemBackClearsVote() throws Exception {
    // Add Verified label.
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());
      u.save();
    }

    // Grant permissions to vote on the verified label.
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
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.VERIFIED, 1).label(LabelId.CODE_REVIEW, 1));

    requestScopeOperations.setApiUser(admin.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));

    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(
            vote(LabelId.CODE_REVIEW, user.id(), 1),
            vote(LabelId.VERIFIED, user.id(), 1),
            vote(LabelId.CODE_REVIEW, admin.id(), 2));

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, admin.id(), 2));

    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.REVIEWER;
    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);

    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, admin.id(), 2));
  }

  @Test
  public void reviewerVotesAreReturnedIfReviewerIsAddedByVoting() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(admin.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, admin.id(), 1));

    gApi.changes().id(r.getChangeId()).reviewer(admin.email()).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS)).hasNoVotes();

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 2));
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, admin.id(), 2));
  }

  @Test
  public void reviewerVotesAreReturnedIfReviewerIsAddedAndThenVoted() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, user.id(), 1));

    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS)).hasNoVotes();

    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.REVIEWER;
    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS)).hasNoVotes();

    gApi.changes()
        .id(r.getChangeId())
        .current()
        .review(new ReviewInput().label(LabelId.CODE_REVIEW, 1));
    assertThat(gApi.changes().id(r.getChangeId()).get(DETAILED_LABELS))
        .hasExactlyVotes(vote(LabelId.CODE_REVIEW, user.id(), 1));
  }

  @Test
  public void addExistingReviewerShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.REVIEWER;

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);
    ReviewerInfo info = result.reviewers.get(0);
    assertThat(info._accountId).isEqualTo(user.id().get());

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).reviewers).isEmpty();
  }

  @Test
  public void addExistingCcShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewerInput input = new ReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.CC;

    ReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.ccs).hasSize(1);
    AccountInfo info = result.ccs.get(0);
    assertThat(info._accountId).isEqualTo(user.id().get());

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).ccs).isEmpty();
  }

  @Test
  public void moveCcToReviewer() throws Exception {
    // Create a change and add 'user' as CC.
    String changeId = createChange().getChangeId();
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = user.email();
    reviewerInput.state = ReviewerState.CC;
    gApi.changes().id(changeId).addReviewer(reviewerInput);

    // Verify that 'user' is a CC on the change and that there are no reviewers.
    ChangeInfo c = gApi.changes().id(changeId).get();
    Collection<AccountInfo> ccs = c.reviewers.get(CC);
    assertThat(ccs).isNotNull();
    assertThat(ccs).hasSize(1);
    assertThat(ccs.iterator().next()._accountId).isEqualTo(user.id().get());
    assertThat(c.reviewers.get(REVIEWER)).isNull();

    // Move 'user' from CC to reviewer.
    gApi.changes().id(changeId).addReviewer(user.id().toString());

    // Verify that 'user' is a reviewer on the change now and that there are no CCs.
    c = gApi.changes().id(changeId).get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.id().get());
    assertThat(c.reviewers.get(CC)).isNull();
  }

  @Test
  public void moveReviewerToCc() throws Exception {
    // Allow everyone to approve changes.
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .update();

    // Create a change and add 'user' as reviewer.
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).addReviewer(user.id().toString());

    // Verify that 'user' is a reviewer on the change and that there are no CCs.
    ChangeInfo c = gApi.changes().id(changeId).get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.id().get());
    assertThat(c.reviewers.get(CC)).isNull();

    // Let 'user' approve the change and verify that the change has the approval.
    requestScopeOperations.setApiUser(user.id());
    approve(changeId);
    c = gApi.changes().id(changeId).get();
    assertThat(c.labels.get(LabelId.CODE_REVIEW).approved._accountId).isEqualTo(user.id().get());

    // Move 'user' from reviewer to CC.
    requestScopeOperations.setApiUser(admin.id());
    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = user.id().toString();
    reviewerInput.state = CC;
    gApi.changes().id(changeId).addReviewer(reviewerInput);

    // Verify that 'user' is a CC on the change now and that there are no reviewers.
    c = gApi.changes().id(changeId).get();
    Collection<AccountInfo> ccs = c.reviewers.get(CC);
    assertThat(ccs).isNotNull();
    assertThat(ccs).hasSize(1);
    assertThat(ccs.iterator().next()._accountId).isEqualTo(user.id().get());
    assertThat(c.reviewers.get(REVIEWER)).isNull();

    // Verify that the approval of 'user' is still there.
    assertThat(c.labels.get(LabelId.CODE_REVIEW).approved._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void wipChangeDoesNotExposeReviewersInChangeSearch() throws Exception {
    requestScopeOperations.setApiUser(admin.id());
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).setWorkInProgress();
    gApi.changes().id(changeId).addReviewer(user.email());
    assertThat(
            gApi.changes()
                .query("project:" + project.get() + " AND reviewer:" + user.email() + "")
                .get())
        .isEmpty();
  }

  private void assertThatUserIsOnlyReviewer(String changeId) throws Exception {
    AccountInfo userInfo = new AccountInfo(user.fullName(), user.getNameEmail().email());
    userInfo._accountId = user.id().get();
    userInfo.username = user.username();
    assertThat(gApi.changes().id(changeId).get().reviewers)
        .containsExactly(ReviewerState.REVIEWER, ImmutableList.of(userInfo));
  }

  private ReviewerResult addReviewer(String changeId, String reviewer) throws Exception {
    return addReviewer(changeId, reviewer, SC_OK);
  }

  private ReviewerResult addReviewer(String changeId, String reviewer, int expectedStatus)
      throws Exception {
    ReviewerInput in = new ReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(changeId, in, expectedStatus);
  }

  private ReviewerResult addReviewer(String changeId, ReviewerInput in) throws Exception {
    return addReviewer(changeId, in, SC_OK);
  }

  private ReviewerResult addReviewer(String changeId, ReviewerInput in, int expectedStatus)
      throws Exception {
    RestResponse resp = adminRestSession.post("/changes/" + changeId + "/reviewers", in);
    return readContentFromJson(resp, expectedStatus, ReviewerResult.class);
  }

  private RestResponse deleteReviewer(String changeId, TestAccount account) throws Exception {
    return adminRestSession.delete("/changes/" + changeId + "/reviewers/" + account.id().get());
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
      jsonReader.setStrictness(Strictness.LENIENT);
      return newGson().fromJson(jsonReader, clazz);
    }
  }

  private static void assertReviewers(
      ChangeInfo c, ReviewerState reviewerState, TestAccount... accounts) throws Exception {
    List<TestAccount> accountList = new ArrayList<>(accounts.length);
    Collections.addAll(accountList, accounts);
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
      expectedAccountIds.add(account.id().get());
    }
    assertThat(actualAccountIds).containsExactlyElementsIn(expectedAccountIds);
  }

  private TestAccount createAccount(String emailPrefix) throws Exception {
    return Iterables.getOnlyElement(createAccounts(1, emailPrefix));
  }

  private List<TestAccount> createAccounts(int n, String emailPrefix) throws Exception {
    List<TestAccount> result = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      result.add(
          accountCreator.create(
              name("u" + i), emailPrefix + "-" + i + "@example.com", "Full Name " + i, null));
    }
    return result;
  }

  private Map<String, LabelInfo> getChangeLabels(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS).labels;
  }

  private static ImmutableList<Account.Id> toAccountIds(List<TestAccount> testAccounts) {
    return testAccounts.stream().map(TestAccount::id).collect(toImmutableList());
  }

  private static Correspondence<ReviewerInfo, Account.Id> hasAccountId() {
    return NullAwareCorrespondence.transforming(
        reviewerInfo -> Account.id(reviewerInfo._accountId), "hasAccountId");
  }
}
