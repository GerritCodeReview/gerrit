// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.PureRevertInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.permissions.PermissionDeniedException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class RevertIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void pureRevertFactBlocksSubmissionOfNonReverts() throws Exception {
    addPureRevertSubmitRule();

    // Create a change that is not a revert of another change
    PushOneCommit.Result r1 = pushFactory.create(user.newIdent(), testRepo).to("refs/for/master");
    approve(r1.getChangeId());

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(r1.getChangeId()).current().submit());
    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to submit 1 change due to the following problems");
    assertThat(thrown).hasMessageThat().contains("needs Is-Pure-Revert");
  }

  @Test
  public void pureRevertFactBlocksSubmissionOfNonPureReverts() throws Exception {
    PushOneCommit.Result r1 = pushFactory.create(user.newIdent(), testRepo).to("refs/for/master");
    merge(r1);

    addPureRevertSubmitRule();

    // Create a revert and push a content change
    String revertId = gApi.changes().id(r1.getChangeId()).revert().get().changeId;
    amendChange(revertId);
    approve(revertId);

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(revertId).current().submit());
    assertThat(thrown)
        .hasMessageThat()
        .contains("Failed to submit 1 change due to the following problems");
    assertThat(thrown).hasMessageThat().contains("needs Is-Pure-Revert");
  }

  @Test
  public void pureRevertFactAllowsSubmissionOfPureReverts() throws Exception {
    // Create a change that we can later revert
    PushOneCommit.Result r1 = pushFactory.create(user.newIdent(), testRepo).to("refs/for/master");
    merge(r1);

    addPureRevertSubmitRule();

    // Create a revert and submit it
    String revertId = gApi.changes().id(r1.getChangeId()).revert().get().changeId;
    approve(revertId);
    gApi.changes().id(revertId).current().submit();
  }

  @Test
  public void pureRevertReturnsTrueForPureRevert() throws Exception {
    PushOneCommit.Result r = createChange();
    merge(r);
    String revertId = gApi.changes().id(r.getChangeId()).revert().get().id;
    // Without query parameter
    assertThat(gApi.changes().id(revertId).pureRevert().isPureRevert).isTrue();
    // With query parameter
    assertThat(
            gApi.changes()
                .id(revertId)
                .pureRevert(
                    projectOperations.project(project).getHead("master").toObjectId().name())
                .isPureRevert)
        .isTrue();
  }

  @Test
  public void pureRevertReturnsFalseOnContentChange() throws Exception {
    PushOneCommit.Result r1 = createChange();
    merge(r1);
    // Create a revert and expect pureRevert to be true
    String revertId = gApi.changes().id(r1.getChangeId()).revert().get().changeId;
    assertThat(gApi.changes().id(revertId).pureRevert().isPureRevert).isTrue();

    // Create a new PS and expect pureRevert to be false
    PushOneCommit.Result result = amendChange(revertId);
    result.assertOkStatus();
    assertThat(gApi.changes().id(revertId).pureRevert().isPureRevert).isFalse();
  }

  @Test
  public void pureRevertParameterTakesPrecedence() throws Exception {
    PushOneCommit.Result r1 = createChange("commit message", "a.txt", "content1");
    merge(r1);
    String oldHead = projectOperations.project(project).getHead("master").toObjectId().name();

    PushOneCommit.Result r2 = createChange("commit message", "a.txt", "content2");
    merge(r2);

    String revertId = gApi.changes().id(r2.getChangeId()).revert().get().changeId;
    assertThat(gApi.changes().id(revertId).pureRevert().isPureRevert).isTrue();
    assertThat(gApi.changes().id(revertId).pureRevert(oldHead).isPureRevert).isFalse();
  }

  @Test
  public void pureRevertReturnsFalseOnInvalidInput() throws Exception {
    PushOneCommit.Result r1 = createChange();
    merge(r1);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(createChange().getChangeId()).pureRevert("invalid id"));
    assertThat(thrown).hasMessageThat().contains("invalid object ID");
  }

  @Test
  public void pureRevertReturnsTrueWithCleanRebase() throws Exception {
    PushOneCommit.Result r1 = createChange("commit message", "a.txt", "content1");
    merge(r1);

    PushOneCommit.Result r2 = createChange("commit message", "b.txt", "content2");
    merge(r2);

    String revertId = gApi.changes().id(r1.getChangeId()).revert().get().changeId;
    // Rebase revert onto HEAD
    gApi.changes().id(revertId).rebase();
    // Check that pureRevert is true which implies that the commit can be rebased onto the original
    // commit.
    assertThat(gApi.changes().id(revertId).pureRevert().isPureRevert).isTrue();
  }

  @Test
  public void pureRevertReturnsFalseWithRebaseConflict() throws Exception {
    // Create an initial commit to serve as claimed original
    PushOneCommit.Result r1 = createChange("commit message", "a.txt", "content1");
    merge(r1);
    String claimedOriginal =
        projectOperations.project(project).getHead("master").toObjectId().name();

    // Change contents of the file to provoke a conflict
    merge(createChange("commit message", "a.txt", "content2"));

    // Create a commit that we can revert
    PushOneCommit.Result r2 = createChange("commit message", "a.txt", "content3");
    merge(r2);

    // Create a revert of r2
    String revertR3Id = gApi.changes().id(r2.getChangeId()).revert().id();
    // Assert that the change is a pure revert of it's 'revertOf'
    assertThat(gApi.changes().id(revertR3Id).pureRevert().isPureRevert).isTrue();
    // Assert that the change is not a pure revert of claimedOriginal because pureRevert is trying
    // to rebase this on claimed original, which fails.
    PureRevertInfo pureRevert = gApi.changes().id(revertR3Id).pureRevert(claimedOriginal);
    assertThat(pureRevert.isPureRevert).isFalse();
  }

  @Test
  public void pureRevertThrowsExceptionWhenChangeIsNotARevertAndNoIdProvided() throws Exception {
    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.changes().id(createChange().getChangeId()).pureRevert());
    assertThat(thrown).hasMessageThat().contains("revertOf not set");
  }

  @Test
  public void revert() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
    ChangeInfo revertChange = gApi.changes().id(r.getChangeId()).revert().get();

    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Patch Set 1: Reverted
    List<ChangeMessageInfo> sourceMessages =
        new ArrayList<>(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(sourceMessages).hasSize(4);
    String expectedMessage =
        String.format("Created a revert of this change as %s", revertChange.changeId);
    assertThat(sourceMessages.get(3).message).isEqualTo(expectedMessage);

    assertThat(revertChange.messages).hasSize(1);
    assertThat(revertChange.messages.iterator().next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(revertChange.revertOf).isEqualTo(gApi.changes().id(r.getChangeId()).get()._number);
  }

  @Test
  public void revertWithDefaultTopic() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).topic("topic");
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    RevertInput revertInput = new RevertInput();
    assertThat(gApi.changes().id(result.getChangeId()).revert(revertInput).topic())
        .isEqualTo("topic");
  }

  @Test
  public void revertWithSetTopic() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).topic("topic");
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    RevertInput revertInput = new RevertInput();
    revertInput.topic = "reverted-not-default";
    assertThat(gApi.changes().id(result.getChangeId()).revert(revertInput).topic())
        .isEqualTo(revertInput.topic);
  }

  @Test
  public void revertWithSetMessage() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    RevertInput revertInput = new RevertInput();
    revertInput.message = "Message from input";
    assertThat(gApi.changes().id(result.getChangeId()).revert(revertInput).get().subject)
        .isEqualTo(revertInput.message);
  }

  @Test
  public void revertNotifications() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    sender.clear();
    ChangeInfo revertChange = gApi.changes().id(r.getChangeId()).revert().get();

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(2);
    assertThat(sender.getMessages(revertChange.changeId, "newchange")).hasSize(1);
    assertThat(sender.getMessages(r.getChangeId(), "revert")).hasSize(1);
  }

  @Test
  public void suppressRevertNotifications() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).addReviewer(user.email());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.NONE;

    sender.clear();
    gApi.changes().id(r.getChangeId()).revert(revertInput).get();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void revertPreservesReviewersAndCcs() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewInput in = ReviewInput.approve();
    in.reviewer(user.email());
    in.reviewer(accountCreator.user2().email(), ReviewerState.CC, true);
    // Add user as reviewer that will create the revert
    in.reviewer(accountCreator.admin2().email());

    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    // expect both the original reviewers and CCs to be preserved
    // original owner should be added as reviewer, user requesting the revert (new owner) removed
    requestScopeOperations.setApiUser(accountCreator.admin2().id());
    Map<ReviewerState, Collection<AccountInfo>> result =
        gApi.changes().id(r.getChangeId()).revert().get().reviewers;
    assertThat(result).containsKey(ReviewerState.REVIEWER);

    List<Integer> reviewers =
        result.get(ReviewerState.REVIEWER).stream().map(a -> a._accountId).collect(toList());
    assertThat(result).containsKey(ReviewerState.CC);
    List<Integer> ccs =
        result.get(ReviewerState.CC).stream().map(a -> a._accountId).collect(toList());
    assertThat(ccs).containsExactly(accountCreator.user2().id().get());
    assertThat(reviewers).containsExactly(user.id().get(), admin.id().get());
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void revertInitialCommit() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(r.getChangeId()).revert());
    assertThat(thrown).hasMessageThat().contains("Cannot revert initial commit");
  }

  @Test
  public void cantRevertNonMergedCommit() throws Exception {
    PushOneCommit.Result result = createChange();
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(result.getChangeId()).revert());
    assertThat(thrown)
        .hasMessageThat()
        .contains("change is " + ChangeUtil.status(result.getChange().change()));
  }

  @Test
  public void cantCreateRevertWithoutProjectWritePermission() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes()
        .id(result.getChangeId())
        .revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    projectCache.checkedGet(project).getProject().setState(ProjectState.READ_ONLY);

    String expected = "project state " + ProjectState.READ_ONLY + " does not permit write";
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(result.getChangeId()).revert());
    assertThat(thrown).hasMessageThat().contains(expected);
  }

  @Test
  public void cantCreateRevertWithoutCreateChangePermission() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/for/*").group(REGISTERED_USERS))
        .update();

    PermissionDeniedException thrown =
        assertThrows(
            PermissionDeniedException.class, () -> gApi.changes().id(r.getChangeId()).revert());
    assertThat(thrown)
        .hasMessageThat()
        .contains("not permitted: create change on refs/heads/master");
  }

  @Test
  public void cantCreateRevertWithoutReadPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.changes().id(r.getChangeId()).revert());
    assertThat(thrown).hasMessageThat().contains("Not found: " + r.getChangeId());
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void cantCreateRevertSubmissionWithoutProjectWritePermission() throws Exception {
    String secondProject = "secondProject";
    projectOperations.newProject().name(secondProject).create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    String topic = "topic";
    PushOneCommit.Result result1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    PushOneCommit.Result result2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic);
    gApi.changes().id(result1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result2.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result1.getChangeId()).revision(result1.getCommit().name()).submit();

    // revoke write permissions for the first repository.
    projectCache.checkedGet(project).getProject().setState(ProjectState.READ_ONLY);

    String expected = "project state " + ProjectState.READ_ONLY + " does not permit write";

    // assert that if first repository has no write permissions, it will fail.
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(result1.getChangeId()).revertSubmission());
    assertThat(thrown).hasMessageThat().contains(expected);

    // assert that if the first repository has no write permissions and a change from another
    // repository is trying to revert the submission, it will fail.
    thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(result2.getChangeId()).revertSubmission());
    assertThat(thrown).hasMessageThat().contains(expected);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void cantCreateRevertSubmissionWithoutCreateChangePermission() throws Exception {
    String secondProject = "secondProject";
    projectOperations.newProject().name(secondProject).create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    String topic = "topic";
    PushOneCommit.Result result1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    PushOneCommit.Result result2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic);
    gApi.changes().id(result1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result2.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result1.getChangeId()).revision(result1.getCommit().name()).submit();

    // revoke create change permissions for the first repository.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/for/*").group(REGISTERED_USERS))
        .update();

    // assert that if first repository has no write create change, it will fail.
    PermissionDeniedException thrown =
        assertThrows(
            PermissionDeniedException.class,
            () -> gApi.changes().id(result1.getChangeId()).revertSubmission());
    assertThat(thrown)
        .hasMessageThat()
        .contains("not permitted: create change on refs/heads/master");

    // assert that if the first repository has no create change permissions and a change from
    // another repository is trying to revert the submission, it will fail.
    thrown =
        assertThrows(
            PermissionDeniedException.class,
            () -> gApi.changes().id(result2.getChangeId()).revertSubmission());
    assertThat(thrown)
        .hasMessageThat()
        .contains("not permitted: create change on refs/heads/master");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void cantCreateRevertSubmissionWithoutReadPermission() throws Exception {
    String secondProject = "secondProject";
    projectOperations.newProject().name(secondProject).create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    String topic = "topic";
    PushOneCommit.Result result1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    PushOneCommit.Result result2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic);
    gApi.changes().id(result1.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result2.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result1.getChangeId()).revision(result1.getCommit().name()).submit();

    // revoke read permissions for the first repository.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    // assert that if first repository has no read permissions, it will fail.
    ResourceNotFoundException resourceNotFoundException =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.changes().id(result1.getChangeId()).revertSubmission());
    assertThat(resourceNotFoundException)
        .hasMessageThat()
        .isEqualTo("Not found: " + result1.getChangeId());

    // assert that if the first repository has no READ permissions and a change from another
    // repository is trying to revert the submission, it will fail.
    AuthException authException =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(result2.getChangeId()).revertSubmission());
    assertThat(authException).hasMessageThat().isEqualTo("read not permitted");
  }

  @Test
  public void revertSubmissionPreservesReviewersAndCcs() throws Exception {
    PushOneCommit.Result r = createChange("first change", "a.txt", "message");

    ReviewInput in = ReviewInput.approve();
    in.reviewer(user.email());
    in.reviewer(accountCreator.user2().email(), ReviewerState.CC, true);
    // Add user as reviewer that will create the revert
    in.reviewer(accountCreator.admin2().email());

    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    // expect both the original reviewers and CCs to be preserved
    // original owner should be added as reviewer, user requesting the revert (new owner) removed
    requestScopeOperations.setApiUser(accountCreator.admin2().id());
    Map<ReviewerState, Collection<AccountInfo>> result =
        gApi.changes()
            .id(r.getChangeId())
            .revertSubmission()
            .collect(toList())
            .get(0)
            .get()
            .reviewers;
    assertThat(result).containsKey(ReviewerState.REVIEWER);

    List<Integer> reviewers =
        result.get(ReviewerState.REVIEWER).stream().map(a -> a._accountId).collect(toList());
    assertThat(result).containsKey(ReviewerState.CC);
    List<Integer> ccs =
        result.get(ReviewerState.CC).stream().map(a -> a._accountId).collect(toList());
    assertThat(ccs).containsExactly(accountCreator.user2().id().get());
    assertThat(reviewers).containsExactly(user.id().get(), admin.id().get());
  }

  @Test
  public void revertSubmissionNotifications() throws Exception {
    PushOneCommit.Result firstResult = createChange("first change", "a.txt", "message");
    approve(firstResult.getChangeId());
    gApi.changes().id(firstResult.getChangeId()).addReviewer(user.email());
    PushOneCommit.Result secondResult = createChange("second change", "b.txt", "other");
    approve(secondResult.getChangeId());
    gApi.changes().id(secondResult.getChangeId()).addReviewer(user.email());

    gApi.changes()
        .id(secondResult.getChangeId())
        .revision(secondResult.getCommit().name())
        .submit();

    sender.clear();
    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.ALL;

    List<ChangeApi> revertChanges =
        gApi.changes()
            .id(secondResult.getChangeId())
            .revertSubmission(revertInput)
            .collect(toList());

    List<Message> messages = sender.getMessages();

    assertThat(messages).hasSize(4);
    assertThat(sender.getMessages(revertChanges.get(0).get().changeId, "newchange")).hasSize(1);
    assertThat(sender.getMessages(firstResult.getChangeId(), "revert")).hasSize(1);
    assertThat(sender.getMessages(revertChanges.get(1).get().changeId, "newchange")).hasSize(1);
    assertThat(sender.getMessages(secondResult.getChangeId(), "revert")).hasSize(1);
  }

  @Test
  public void suppressRevertSubmissionNotifications() throws Exception {
    PushOneCommit.Result firstResult = createChange("first change", "a.txt", "message");
    approve(firstResult.getChangeId());
    gApi.changes().id(firstResult.getChangeId()).addReviewer(user.email());
    PushOneCommit.Result secondResult = createChange("second change", "b.txt", "other");
    approve(secondResult.getChangeId());
    gApi.changes().id(secondResult.getChangeId()).addReviewer(user.email());

    gApi.changes()
        .id(secondResult.getChangeId())
        .revision(secondResult.getCommit().name())
        .submit();

    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.NONE;

    sender.clear();
    gApi.changes().id(secondResult.getChangeId()).revertSubmission(revertInput);
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void revertSubmissionOfSingleChange() throws Exception {
    PushOneCommit.Result result = createChange("Change", "a.txt", "message");
    approve(result.getChangeId());
    gApi.changes().id(result.getChangeId()).current().submit();
    List<ChangeApi> revertChanges =
        gApi.changes().id(result.getChangeId()).revertSubmission().collect(toList());

    String sha1Commit = result.getCommit().getName();

    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1Commit);

    assertThat(revertChanges.get(0).current().files().get("a.txt").linesDeleted).isEqualTo(1);

    assertThat(revertChanges.get(0).get().revertOf)
        .isEqualTo(result.getChange().change().getChangeId());
    assertThat(revertChanges.get(0).get().topic)
        .startsWith(
            "revert-"
                + result.getChange().change().getSubmissionId()
                + "-"
                + TimeUtil.nowTs()
                    .toString()
                    .substring(
                        0,
                        10)); // substring(0,10) ensures at least the date of the topic is correct.
  }

  @Test
  public void revertSubmissionWithSetTopic() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).topic("topic");
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    RevertInput revertInput = new RevertInput();
    revertInput.topic = "reverted-not-default";
    assertThat(
            gApi.changes()
                .id(result.getChangeId())
                .revertSubmission(revertInput)
                .collect(toList())
                .get(0)
                .topic())
        .isEqualTo(revertInput.topic);
  }

  @Test
  public void revertSubmissionWithSetMessage() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();
    RevertInput revertInput = new RevertInput();
    revertInput.message = "Message from input";
    assertThat(
            gApi.changes()
                .id(result.getChangeId())
                .revertSubmission(revertInput)
                .collect(toList())
                .get(0)
                .get()
                .subject)
        .isEqualTo(revertInput.message);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  @UseClockStep
  public void revertSubmissionDifferentRepositoriesWithDependantChange() throws Exception {
    projectOperations.newProject().name("secondProject").create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    List<PushOneCommit.Result> resultCommits = new ArrayList<>();
    String topic = "topic";
    resultCommits.add(createChange(testRepo, "master", "first change", "a.txt", "message", topic));
    resultCommits.add(
        createChange(secondRepo, "master", "first change", "a.txt", "message", topic));
    resultCommits.add(
        createChange(secondRepo, "master", "second change", "b.txt", "Other message", topic));
    for (PushOneCommit.Result result : resultCommits) {
      approve(result.getChangeId());
    }
    // submit all changes
    gApi.changes().id(resultCommits.get(1).getChangeId()).current().submit();
    List<ChangeApi> revertChanges =
        gApi.changes().id(resultCommits.get(1).getChangeId()).revertSubmission().collect(toList());

    // The reverts are by update time, so the reversal ensures that
    // revertChanges[i] is the revert of resultCommits[i]
    Collections.reverse(revertChanges);

    assertThat(revertChanges.get(0).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(2).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    // has size 3 because of the same topic, and submitWholeTopic is true.
    assertThat(gApi.changes().id(revertChanges.get(0).get()._number).submittedTogether())
        .hasSize(3);

    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Created a revert of this change as %s

    for (int i = 0; i < resultCommits.size(); i++) {
      assertThat(revertChanges.get(i).current().commit(false).parents.get(0).commit)
          .isEqualTo(resultCommits.get(i).getCommit().getName());
      assertThat(revertChanges.get(i).get().revertOf)
          .isEqualTo(resultCommits.get(i).getChange().change().getChangeId());
      List<ChangeMessageInfo> sourceMessages =
          new ArrayList<>(gApi.changes().id(resultCommits.get(i).getChangeId()).get().messages);
      assertThat(sourceMessages).hasSize(4);
      String expectedMessage =
          String.format(
              "Created a revert of this change as %s", revertChanges.get(i).get().changeId);
      assertThat(sourceMessages.get(3).message).isEqualTo(expectedMessage);
      // Expected message on the created change: "Uploaded patch set 1."
      List<ChangeMessageInfo> messages =
          revertChanges.get(i).get().messages.stream().collect(toList());
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).message).isEqualTo("Uploaded patch set 1.");
      assertThat(revertChanges.get(i).get().revertOf)
          .isEqualTo(gApi.changes().id(resultCommits.get(i).getChangeId()).get()._number);
      assertThat(revertChanges.get(i).get().topic)
          .startsWith("revert-" + resultCommits.get(0).getChange().change().getSubmissionId());
    }
  }

  @Test
  public void cantRevertSubmissionWithAnOpenChange() throws Exception {
    PushOneCommit.Result result = createChange("first change", "a.txt", "message");
    approve(result.getChangeId());
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.changes().id(result.getChangeId()).revertSubmission());
    assertThat(thrown).hasMessageThat().isEqualTo("change is new.");
  }

  @Override
  protected PushOneCommit.Result createChange() throws Exception {
    return createChange("refs/for/master");
  }

  @Override
  protected PushOneCommit.Result createChange(String ref) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to(ref);
    result.assertOkStatus();
    return result;
  }

  private void addPureRevertSubmitRule() throws Exception {
    modifySubmitRules(
        "submit_rule(submit(R)) :- \n"
            + "gerrit:pure_revert(1), \n"
            + "!,"
            + "gerrit:uploader(U), \n"
            + "R = label('Is-Pure-Revert', ok(U)).\n"
            + "submit_rule(submit(R)) :- \n"
            + "gerrit:pure_revert(U), \n"
            + "U \\= 1,"
            + "R = label('Is-Pure-Revert', need(_)). \n\n");
  }

  private void modifySubmitRules(String newContent) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .author(admin.newIdent())
          .committer(admin.newIdent())
          .add("rules.pl", newContent)
          .message("Modify rules.pl")
          .create();
    }
  }
}
