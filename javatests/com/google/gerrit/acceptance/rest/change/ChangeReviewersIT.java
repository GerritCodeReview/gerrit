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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REMOVED;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewResult;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.ReviewerUpdateInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ReviewerAdder;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.gerrit.testing.FakeEmailSender.Message;
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

  @Test
  public void addGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = groupOperations.newGroup().name("largeGroup").create().get();
    String mediumGroup = groupOperations.newGroup().name("mediumGroup").create().get();

    int largeGroupSize = ReviewerAdder.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = ReviewerAdder.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
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
    in.reviewer = user.email();
    in.state = CC;
    AddReviewerResult result = addReviewer(changeId, in);

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
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains(admin.fullName() + " has uploaded this change for review.");
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
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = groupOperations.newGroup().name("cc1").create().get();
    in.state = CC;
    gApi.groups()
        .id(in.reviewer)
        .addMembers(firstUsernames.toArray(new String[firstUsernames.size()]));
    AddReviewerResult result = addReviewer(changeId, in);

    assertThat(result.input).isEqualTo(in.reviewer);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).isNull();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, CC, firstUsers);

    // Verify emails were sent to each of the group's accounts.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    List<Address> expectedAddresses = new ArrayList<>(firstUsers.size());
    for (TestAccount u : firstUsers) {
      expectedAddresses.add(u.getEmailAddress());
    }
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);

    // CC a group that overlaps with some existing reviewers and CCed accounts.
    TestAccount reviewer =
        accountCreator.create(name("reviewer"), "addCcGroup-reviewer@example.com", "Reviewer");
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
      expectedAddresses.add(users.get(users.size() - i - 1).getEmailAddress());
    }
    expectedAddresses.add(reviewer.getEmailAddress());
    assertThat(m.rcpt()).containsExactlyElementsIn(expectedAddresses);
  }

  @Test
  public void transitionCcToReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
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
    LabelInfo label = c.labels.get("Code-Review");
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
    LabelInfo label = c.labels.get("Code-Review");
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
    LabelInfo label = c.labels.get("Code-Review");
    assertThat(label).isNotNull();
    assertThat(label.all).isNull();

    // Add user as REVIEWER.
    ReviewInput input = new ReviewInput().reviewer(user.username());
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
    assertThat(approvals).containsEntry(admin.id().get(), 0);
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
    assertThat(approvals).containsEntry(admin.id().get(), 0);
    assertThat(approvals).containsEntry(user.id().get(), 0);
  }

  private void setupLabelLock(AccountGroup.UUID group) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      LabelType labelLock = TestLabels.labelLock();
      u.getConfig().getLabelSections().put(labelLock.getName(), labelLock);
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel("Label-Lock").ref("refs/heads/*").group(group).range(0, 1))
        .update();
  }

  private PushOneCommit.Result createTestChangeAsUser(TestAccount owner) throws Exception {
    PushOneCommit push = pushFactory.create(owner.newIdent(), testRepo);
    return push.to("refs/for/master");
  }

  private AccountGroup.UUID createGroupWithLabelLockPerms(String groupName) throws Exception {
    AccountGroup.UUID accountGroup = groupOperations.newGroup().name(groupName).create();
    setupLabelLock(accountGroup);
    return accountGroup;
  }

  private void addMembersToGroup(AccountGroup.UUID accountGroup, TestAccount user)
      throws RestApiException {
    String group = groupOperations.group(accountGroup).get().name();
    gApi.groups().id(group).addMembers(user.username());
  }

  private void labelLockChangeAsUser(PushOneCommit.Result r, TestAccount user) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ReviewInput reviewInput = new ReviewInput().reviewer(user.email()).label("Label-Lock", 1);
    ReviewResult labelLockResult =
        gApi.changes().id(r.getChangeId()).revision("current").review(reviewInput);
    assertThat(Iterables.getOnlyElement(labelLockResult.labels.keySet())).isEqualTo("Label-Lock");
    assertThat(Iterables.getOnlyElement(labelLockResult.labels.values())).isEqualTo(1);
    assertThat(Iterables.getOnlyElement(labelLockResult.reviewers.keySet()))
        .isEqualTo(user.email());
  }

  private PushOneCommit.Result createLabelLockedChange(TestAccount botUser) throws Exception {
    PushOneCommit.Result change = createTestChangeAsUser(user);
    AccountGroup.UUID botGroup = createGroupWithLabelLockPerms("Bots");
    addMembersToGroup(botGroup, botUser);
    labelLockChangeAsUser(change, botUser);
    return change;
  }

  @Test
  public void reviewerVoteOnLabelLockedChanges() throws Exception {
    TestAccount botUser = accountCreator.user3();
    PushOneCommit.Result change = createLabelLockedChange(botUser);

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(change.getChangeId())
                    .revision("current")
                    .review(new ReviewInput().reviewer(user.email()).label("Code-Review", 1)));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Applying label \"Code-Review\": 1 is restricted since change is label locked");
  }

  @Test
  public void reviewerCommentOnLabelLockedChanges() throws Exception {
    TestAccount botUser = accountCreator.user3();
    PushOneCommit.Result change = createLabelLockedChange(botUser);

    TestAccount reviewer = accountCreator.user();
    requestScopeOperations.setApiUser(reviewer.id());
    ReviewInput.CommentInput c = new ReviewInput.CommentInput();
    c.line = 1;
    c.message = "comment after label lock";
    c.path = FILE_NAME;
    ReviewInput in = new ReviewInput();
    in.comments = new HashMap<>();
    in.comments.put(c.path, Lists.newArrayList(c));
    gApi.changes().id(change.getChangeId()).revision(change.getCommit().name()).review(in);
    Collection<CommentInfo> comments =
        gApi.changes().id(change.getChangeId()).comments().values().stream()
            .flatMap(Collection::stream)
            .collect(toList());
    assertThat(comments.stream().map(x -> x.message)).containsExactly("comment after label lock");
  }

  @Test
  public void adminUserRemoveLabelLock() throws Exception {
    TestAccount botUser = accountCreator.user3();
    PushOneCommit.Result change = createLabelLockedChange(botUser);

    requestScopeOperations.setApiUser(admin.id());
    DeleteReviewerInput input = new DeleteReviewerInput();
    gApi.changes().id(change.getChangeId()).reviewer(botUser.id().toString()).remove(input);
    Collection<AccountInfo> reviewers =
        gApi.changes().id(change.getChangeId()).get().reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(1);
  }

  @Test
  public void regularUserRemoveLabelLock() throws Exception {
    TestAccount botUser = accountCreator.user3();
    PushOneCommit.Result change = createLabelLockedChange(botUser);

    TestAccount regularUser = accountCreator.user();
    requestScopeOperations.setApiUser(regularUser.id());
    DeleteReviewerInput input = new DeleteReviewerInput();
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () ->
                gApi.changes()
                    .id(change.getChangeId())
                    .reviewer(botUser.id().toString())
                    .remove(input));
    assertThat(thrown)
        .hasMessageThat()
        .contains("not allowed to remove reviewer since label lock is set");
  }

  @Test
  public void resetOfLockedLabel() throws Exception {
    TestAccount botUser = accountCreator.user3();
    PushOneCommit.Result change = createLabelLockedChange(botUser);

    requestScopeOperations.setApiUser(botUser.id());
    ReviewResult resultLabelUnlock =
        gApi.changes()
            .id(change.getChangeId())
            .revision("current")
            .review(new ReviewInput().reviewer(botUser.email()).label("Label-Lock", 0));
    assertThat(Iterables.getOnlyElement(resultLabelUnlock.labels.keySet())).isEqualTo("Label-Lock");
    assertThat(Iterables.getOnlyElement(resultLabelUnlock.reviewers.keySet()))
        .isEqualTo(botUser.email());
  }

  @Test
  public void resetOfLockedLabelsFromMultipleUsers() throws Exception {
    TestAccount labelLockUser1 = accountCreator.user2();
    TestAccount labelLockUser2 = accountCreator.user3();
    TestAccount uploader = accountCreator.user();
    AccountGroup.UUID botGroup = createGroupWithLabelLockPerms("Bots");
    addMembersToGroup(botGroup, labelLockUser1);
    addMembersToGroup(botGroup, labelLockUser2);
    PushOneCommit.Result change = createTestChangeAsUser(uploader);

    labelLockChangeAsUser(change, labelLockUser1);
    labelLockChangeAsUser(change, labelLockUser2);

    // Check if labelLockUser1 can unlock its label lock while
    // another label lock is set by labelLockUser2
    requestScopeOperations.setApiUser(labelLockUser1.id());
    ReviewResult resultLabelUnlock1 =
        gApi.changes()
            .id(change.getChangeId())
            .revision("current")
            .review(new ReviewInput().reviewer(labelLockUser1.email()).label("Label-Lock", 0));
    assertThat(Iterables.getOnlyElement(resultLabelUnlock1.labels.keySet()))
        .isEqualTo("Label-Lock");
    assertThat(Iterables.getOnlyElement(resultLabelUnlock1.reviewers.keySet()))
        .isEqualTo(labelLockUser1.email());
    assertThat(Iterables.getOnlyElement(resultLabelUnlock1.labels.values())).isEqualTo(0);

    requestScopeOperations.setApiUser(labelLockUser2.id());
    ReviewResult resultLabelUnlock2 =
        gApi.changes()
            .id(change.getChangeId())
            .revision("current")
            .review(new ReviewInput().reviewer(labelLockUser2.email()).label("Label-Lock", 0));
    assertThat(Iterables.getOnlyElement(resultLabelUnlock2.labels.keySet()))
        .isEqualTo("Label-Lock");
    assertThat(Iterables.getOnlyElement(resultLabelUnlock2.reviewers.keySet()))
        .isEqualTo(labelLockUser2.email());
    assertThat(Iterables.getOnlyElement(resultLabelUnlock2.labels.values())).isEqualTo(0);
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

    // Verify reviewer and CC were added. If not in NoteDb read mode, both
    // parties will be returned as CCed.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertReviewers(c, REVIEWER, admin, user);
    assertReviewers(c, CC, observer);

    // Verify emails were sent to added reviewers.
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(2);

    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress(), observer.getEmailAddress());
    assertThat(m.body()).contains(admin.fullName() + " has posted comments on this change.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+2");

    m = messages.get(1);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress(), observer.getEmailAddress());
    assertThat(m.body()).contains("Hello " + user.fullName() + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
  }

  @Test
  public void reviewAndAddGroupReviewers() throws Exception {
    int largeGroupSize = ReviewerAdder.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = ReviewerAdder.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
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
  public void noteDbAddReviewerToReviewerChangeInfo() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email();
    in.state = CC;
    addReviewer(changeId, in);

    in.state = REVIEWER;
    addReviewer(changeId, in);

    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    requestScopeOperations.setApiUser(user.id());
    // NoteDb adds reviewer to a change on every review.
    gApi.changes().id(changeId).current().review(ReviewInput.dislike());

    deleteReviewer(changeId, user).assertNoContent();

    ChangeInfo c = gApi.changes().id(changeId).get();
    assertThat(c.reviewerUpdates).isNotNull();
    assertThat(c.reviewerUpdates).hasSize(3);

    Iterator<ReviewerUpdateInfo> it = c.reviewerUpdates.iterator();
    ReviewerUpdateInfo reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(CC);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.id().get());

    reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(REVIEWER);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.id().get());

    reviewerChange = it.next();
    assertThat(reviewerChange.state).isEqualTo(REMOVED);
    assertThat(reviewerChange.reviewer._accountId).isEqualTo(user.id().get());
    assertThat(reviewerChange.updatedBy._accountId).isEqualTo(admin.id().get());
  }

  @Test
  public void addDuplicateReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    ReviewInput input = ReviewInput.approve().reviewer(user.email()).reviewer(user.email());
    ReviewResult result = review(r.getChangeId(), r.getCommit().name(), input);
    assertThat(result.reviewers).isNotNull();
    assertThat(result.reviewers).hasSize(1);
    AddReviewerResult reviewerResult = result.reviewers.get(user.email());
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
    String group1 = groupOperations.newGroup().name("group1").create().get();
    String group2 = groupOperations.newGroup().name("group2").create().get();
    gApi.groups().id(group1).addMembers(user1.username(), user2.username());
    gApi.groups().id(group2).addMembers(user2.username(), user3.username());

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
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.getEmailAddress());
  }

  @Test
  public void notifyDetailsWorkOnPostReviewers() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount userToNotify = createAccounts(1, "notify-details-post-reviewers").get(0);

    AddReviewerInput addReviewer = new AddReviewerInput();
    addReviewer.reviewer = user.email();
    addReviewer.notify = NotifyHandling.NONE;
    addReviewer.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email())));

    sender.clear();
    gApi.changes().id(r.getChangeId()).addReviewer(addReviewer);
    assertThat(sender.getMessages()).hasSize(1);
    assertThat(sender.getMessages().get(0).rcpt()).containsExactly(userToNotify.getEmailAddress());
  }

  @Test
  public void removeReviewerWithVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(r.getChangeId()).current().review(new ReviewInput().label("Code-Review", 1));
    requestScopeOperations.setApiUser(newUser.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("remove reviewer not permitted");
  }

  @Test
  @Sandboxed
  public void removeReviewerWithoutVoteWithPermissionSucceeds() throws Exception {
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
    gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove();
    assertThat(gApi.changes().id(r.getChangeId()).get().reviewers).isEmpty();
  }

  @Test
  public void removeReviewerWithoutVoteWithoutPermissionFails() throws Exception {
    PushOneCommit.Result r = createChange();
    TestAccount newUser = createAccounts(1, name("foo")).get(0);

    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    requestScopeOperations.setApiUser(newUser.id());
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

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.CC;
    gApi.changes().id(r.getChangeId()).addReviewer(input);
    requestScopeOperations.setApiUser(newUser.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(r.getChangeId()).reviewer(user.email()).remove());
    assertThat(thrown).hasMessageThat().contains("remove reviewer not permitted");
  }

  @Test
  public void addExistingReviewerShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.REVIEWER;

    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.reviewers).hasSize(1);
    ReviewerInfo info = result.reviewers.get(0);
    assertThat(info._accountId).isEqualTo(user.id().get());

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).reviewers).isEmpty();
  }

  @Test
  public void addExistingCcShortCircuits() throws Exception {
    PushOneCommit.Result r = createChange();

    AddReviewerInput input = new AddReviewerInput();
    input.reviewer = user.email();
    input.state = ReviewerState.CC;

    AddReviewerResult result = gApi.changes().id(r.getChangeId()).addReviewer(input);
    assertThat(result.ccs).hasSize(1);
    AccountInfo info = result.ccs.get(0);
    assertThat(info._accountId).isEqualTo(user.id().get());

    assertThat(gApi.changes().id(r.getChangeId()).addReviewer(input).ccs).isEmpty();
  }

  private void assertThatUserIsOnlyReviewer(String changeId) throws Exception {
    AccountInfo userInfo = new AccountInfo(user.fullName(), user.getEmailAddress().getEmail());
    userInfo._accountId = user.id().get();
    userInfo.username = user.username();
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
      jsonReader.setLenient(true);
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
