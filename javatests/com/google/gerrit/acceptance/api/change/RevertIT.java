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
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
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
import com.google.gerrit.extensions.common.RevertSubmissionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.permissions.PermissionDeniedException;
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
import org.eclipse.jgit.transport.RefSpec;
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
  public void revertChangeWithLongSubject() throws Exception {
    String changeTitle =
        "This change has a very long title and therefore it will be cut to 50 characters when the"
            + " revert change will revert this change";
    String result = createChange(changeTitle, "a.txt", "message").getChangeId();
    gApi.changes().id(result).current().review(ReviewInput.approve());
    gApi.changes().id(result).current().submit();
    RevertInput revertInput = new RevertInput();
    ChangeInfo revertChange = gApi.changes().id(result).revert(revertInput).get();
    assertThat(revertChange.subject)
        .isEqualTo(String.format("Revert \"%s...\"", changeTitle.substring(0, 59)));
    assertThat(gApi.changes().id(revertChange.id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"%s...\"\n\nThis reverts commit %s.\n\nChange-Id: %s\n",
                changeTitle.substring(0, 59),
                gApi.changes().id(result).get().currentRevision,
                revertChange.changeId));
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
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateProject(p -> p.setState(ProjectState.READ_ONLY));
      u.save();
    }

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
  public void revertNotAllowedForOwnerWithoutRevertPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.REVERT).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result result = createChange();
    approve(result.getChangeId());
    gApi.changes().id(result.getChangeId()).current().submit();
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(result.getChangeId()).revert());
    assertThat(thrown).hasMessageThat().contains("revert not permitted");
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void cantCreateRevertSubmissionWithoutProjectWritePermission() throws Exception {
    String secondProject = "secondProject";
    projectOperations.newProject().name(secondProject).create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    String topic = "topic";
    String change1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic).getChangeId();
    String change2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic)
            .getChangeId();
    gApi.changes().id(change1).current().review(ReviewInput.approve());
    gApi.changes().id(change2).current().review(ReviewInput.approve());
    gApi.changes().id(change1).current().submit();

    // revoke write permissions for the first repository.
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateProject(p -> p.setState(ProjectState.READ_ONLY));
      u.save();
    }

    String expected = "project state " + ProjectState.READ_ONLY + " does not permit write";

    // assert that if first repository has no write permissions, it will fail.
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(change1).revertSubmission());
    assertThat(thrown).hasMessageThat().contains(expected);

    // assert that if the first repository has no write permissions and a change from another
    // repository is trying to revert the submission, it will fail.
    thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(change2).revertSubmission());
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
    String change1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic).getChangeId();
    String change2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic)
            .getChangeId();
    gApi.changes().id(change1).current().review(ReviewInput.approve());
    gApi.changes().id(change2).current().review(ReviewInput.approve());
    gApi.changes().id(change1).current().submit();

    // revoke create change permissions for the first repository.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/for/*").group(REGISTERED_USERS))
        .update();

    // assert that if first repository has no write create change, it will fail.
    PermissionDeniedException thrown =
        assertThrows(
            PermissionDeniedException.class, () -> gApi.changes().id(change1).revertSubmission());
    assertThat(thrown)
        .hasMessageThat()
        .contains("not permitted: create change on refs/heads/master");

    // assert that if the first repository has no create change permissions and a change from
    // another repository is trying to revert the submission, it will fail.
    thrown =
        assertThrows(
            PermissionDeniedException.class, () -> gApi.changes().id(change2).revertSubmission());
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
    String change1 =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic).getChangeId();
    String change2 =
        createChange(secondRepo, "master", "second change", "b.txt", "message", topic)
            .getChangeId();
    gApi.changes().id(change1).current().review(ReviewInput.approve());
    gApi.changes().id(change2).current().review(ReviewInput.approve());
    gApi.changes().id(change1).current().submit();

    // revoke read permissions for the first repository.
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    // assert that if first repository has no read permissions, it will fail.
    ResourceNotFoundException resourceNotFoundException =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.changes().id(change1).revertSubmission());
    assertThat(resourceNotFoundException).hasMessageThat().isEqualTo("Not found: " + change1);

    // assert that if the first repository has no READ permissions and a change from another
    // repository is trying to revert the submission, it will fail.
    AuthException authException =
        assertThrows(AuthException.class, () -> gApi.changes().id(change2).revertSubmission());
    assertThat(authException).hasMessageThat().isEqualTo("read not permitted");
  }

  @Test
  public void revertSubmissionNotAllowedForOwnerWithoutRevertPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.REVERT).ref("refs/heads/master").group(REGISTERED_USERS))
        .update();

    PushOneCommit.Result result = createChange();
    approve(result.getChangeId());
    gApi.changes().id(result.getChangeId()).current().submit();
    requestScopeOperations.setApiUser(user.id());

    AuthException thrown =
        assertThrows(
            AuthException.class, () -> gApi.changes().id(result.getChangeId()).revertSubmission());
    assertThat(thrown).hasMessageThat().contains("revert not permitted");
  }

  @Test
  public void revertSubmissionPreservesReviewersAndCcs() throws Exception {
    String change = createChange("first change", "a.txt", "message").getChangeId();

    ReviewInput in = ReviewInput.approve();
    in.reviewer(user.email());
    in.reviewer(accountCreator.user2().email(), ReviewerState.CC, true);
    // Add user as reviewer that will create the revert
    in.reviewer(accountCreator.admin2().email());

    gApi.changes().id(change).current().review(in);
    gApi.changes().id(change).current().submit();

    // expect both the original reviewers and CCs to be preserved
    // original owner should be added as reviewer, user requesting the revert (new owner) removed
    requestScopeOperations.setApiUser(accountCreator.admin2().id());

    Map<ReviewerState, Collection<AccountInfo>> result =
        getChangeApis(gApi.changes().id(change).revertSubmission()).get(0).get().reviewers;
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
    String firstResult = createChange("first change", "a.txt", "message").getChangeId();
    approve(firstResult);
    gApi.changes().id(firstResult).addReviewer(user.email());
    String secondResult = createChange("second change", "b.txt", "other").getChangeId();
    approve(secondResult);
    gApi.changes().id(secondResult).addReviewer(user.email());

    gApi.changes().id(secondResult).current().submit();

    sender.clear();
    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.ALL;

    RevertSubmissionInfo revertChanges =
        gApi.changes().id(secondResult).revertSubmission(revertInput);

    List<Message> messages = sender.getMessages();

    assertThat(messages).hasSize(4);
    assertThat(sender.getMessages(revertChanges.revertChanges.get(0).changeId, "newchange"))
        .hasSize(1);
    assertThat(sender.getMessages(firstResult, "revert")).hasSize(1);
    assertThat(sender.getMessages(revertChanges.revertChanges.get(1).changeId, "newchange"))
        .hasSize(1);
    assertThat(sender.getMessages(secondResult, "revert")).hasSize(1);
  }

  @Test
  public void revertSubmissionIdenticalTreeIsAllowed() throws Exception {
    String unrelatedChange = createChange("change1", "a.txt", "message").getChangeId();
    approve(unrelatedChange);
    gApi.changes().id(unrelatedChange).current().submit();

    String emptyChange = createChange("change1", "a.txt", "message").getChangeId();
    approve(emptyChange);
    String changeToBeReverted = createChange("change2", "b.txt", "message").getChangeId();
    approve(changeToBeReverted);

    gApi.changes().id(changeToBeReverted).current().submit();

    sender.clear();
    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.ALL;

    List<ChangeApi> revertChanges =
        getChangeApis(gApi.changes().id(changeToBeReverted).revertSubmission(revertInput));
    assertThat(revertChanges.size()).isEqualTo(2);
  }

  @Test
  public void suppressRevertSubmissionNotifications() throws Exception {
    String firstResult = createChange("first change", "a.txt", "message").getChangeId();
    approve(firstResult);
    gApi.changes().id(firstResult).addReviewer(user.email());
    String secondResult = createChange("second change", "b.txt", "other").getChangeId();
    approve(secondResult);
    gApi.changes().id(secondResult).addReviewer(user.email());

    gApi.changes().id(secondResult).current().submit();

    RevertInput revertInput = new RevertInput();
    revertInput.notify = NotifyHandling.NONE;

    sender.clear();
    gApi.changes().id(secondResult).revertSubmission(revertInput);
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void revertSubmissionOfSingleChange() throws Exception {
    PushOneCommit.Result result = createChange("Change", "a.txt", "message");
    String resultId = result.getChangeId();
    approve(resultId);
    gApi.changes().id(resultId).current().submit();
    List<ChangeApi> revertChanges = getChangeApis(gApi.changes().id(resultId).revertSubmission());

    String sha1Commit = result.getCommit().getName();

    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1Commit);

    assertThat(revertChanges.get(0).current().files().get("a.txt").linesDeleted).isEqualTo(1);

    assertThat(revertChanges.get(0).get().revertOf)
        .isEqualTo(result.getChange().change().getChangeId());
    assertThat(revertChanges.get(0).get().topic)
        .startsWith("revert-" + result.getChange().change().getSubmissionId() + "-");
  }

  @Test
  public void revertSubmissionWithSetTopic() throws Exception {
    String result = createChange().getChangeId();
    gApi.changes().id(result).current().review(ReviewInput.approve());
    gApi.changes().id(result).topic("topic");
    gApi.changes().id(result).current().submit();
    RevertInput revertInput = new RevertInput();
    revertInput.topic = "reverted-not-default";
    assertThat(gApi.changes().id(result).revertSubmission(revertInput).revertChanges.get(0).topic)
        .isEqualTo(revertInput.topic);
  }

  @Test
  public void revertSubmissionWithSetMessage() throws Exception {
    String firstResult = createChange("first change", "a.txt", "message").getChangeId();
    String secondResult = createChange("second change", "b.txt", "message").getChangeId();
    approve(firstResult);
    approve(secondResult);
    gApi.changes().id(secondResult).current().submit();
    RevertInput revertInput = new RevertInput();
    String commitMessage = "Message from input";
    revertInput.message = commitMessage;
    List<ChangeInfo> revertChanges =
        gApi.changes().id(firstResult).revertSubmission(revertInput).revertChanges;
    assertThat(revertChanges.get(0).subject).isEqualTo("Revert \"first change\"");
    assertThat(gApi.changes().id(revertChanges.get(0).id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"first change\"\n\n%s\n\nChange-Id: %s\n",
                commitMessage, revertChanges.get(0).changeId));
    assertThat(revertChanges.get(1).subject).isEqualTo("Revert \"second change\"");
    assertThat(gApi.changes().id(revertChanges.get(1).id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"second change\"\n\n%s\n\nChange-Id: %s\n",
                commitMessage, revertChanges.get(1).changeId));
  }

  @Test
  public void revertSubmissionWithoutMessage() throws Exception {
    String firstResult = createChange("first change", "a.txt", "message").getChangeId();
    String secondResult = createChange("second change", "b.txt", "message").getChangeId();
    approve(firstResult);
    approve(secondResult);
    gApi.changes().id(secondResult).current().submit();
    RevertInput revertInput = new RevertInput();
    List<ChangeInfo> revertChanges =
        gApi.changes().id(firstResult).revertSubmission(revertInput).revertChanges;
    assertThat(revertChanges.get(0).subject).isEqualTo("Revert \"first change\"");
    assertThat(gApi.changes().id(revertChanges.get(0).id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"first change\"\n\nThis reverts commit %s.\n\nChange-Id: %s\n",
                gApi.changes().id(firstResult).get().currentRevision,
                revertChanges.get(0).changeId));
    assertThat(revertChanges.get(1).subject).isEqualTo("Revert \"second change\"");
    assertThat(gApi.changes().id(revertChanges.get(1).id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"second change\"\n\nThis reverts commit %s.\n\nChange-Id: %s\n",
                gApi.changes().id(secondResult).get().currentRevision,
                revertChanges.get(1).changeId));
  }

  @Test
  public void revertSubmissionRevertsChangeWithLongSubject() throws Exception {
    String changeTitle =
        "This change has a very long title and therefore it will be cut to 56 characters when the"
            + " revert change will revert this change";
    String result = createChange(changeTitle, "a.txt", "message").getChangeId();
    gApi.changes().id(result).current().review(ReviewInput.approve());
    gApi.changes().id(result).current().submit();
    RevertInput revertInput = new RevertInput();
    ChangeInfo revertChange =
        gApi.changes().id(result).revertSubmission(revertInput).revertChanges.get(0);
    assertThat(revertChange.subject)
        .isEqualTo(String.format("Revert \"%s...\"", changeTitle.substring(0, 56)));
    assertThat(gApi.changes().id(revertChange.id).current().commit(false).message)
        .isEqualTo(
            String.format(
                "Revert \"%s...\"\n\nThis reverts commit %s.\n\nChange-Id: %s\n",
                changeTitle.substring(0, 56),
                gApi.changes().id(result).get().currentRevision,
                revertChange.changeId));
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionDifferentRepositoriesWithDependantChange() throws Exception {
    projectOperations.newProject().name("secondProject").create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    List<PushOneCommit.Result> resultCommits = new ArrayList<>();
    String topic = "topic";
    resultCommits.add(
        createChange(secondRepo, "master", "first change", "a.txt", "message", topic));
    resultCommits.add(
        createChange(secondRepo, "master", "second change", "b.txt", "Other message", topic));
    resultCommits.add(
        createChange(testRepo, "master", "main repo change", "a.txt", "message", topic));
    for (PushOneCommit.Result result : resultCommits) {
      approve(result.getChangeId());
    }
    // submit all changes
    gApi.changes().id(resultCommits.get(1).getChangeId()).current().submit();
    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(resultCommits.get(1).getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);

    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);

    assertThat(revertChanges).hasSize(3);

    String sha1RevertOfTheSecondChange = revertChanges.get(1).current().commit(false).commit;
    String sha1SecondChange = resultCommits.get(1).getCommit().getName();
    String sha1ThirdChange = resultCommits.get(2).getCommit().getName();
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1RevertOfTheSecondChange);
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondChange);
    assertThat(revertChanges.get(2).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1ThirdChange);

    assertThat(revertChanges.get(0).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(2).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    // has size 3 because of the same topic, and submitWholeTopic is true.
    assertThat(gApi.changes().id(revertChanges.get(0).get()._number).submittedTogether())
        .hasSize(3);

    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Created a revert of this change as %s

    for (int i = 0; i < resultCommits.size(); i++) {
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

    assertThat(gApi.changes().id(revertChanges.get(1).id()).current().related().changes).hasSize(2);
  }

  @Test
  public void cantRevertSubmissionWithAnOpenChange() throws Exception {
    String result = createChange("change", "a.txt", "message").getChangeId();
    approve(result);
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> gApi.changes().id(result).revertSubmission());
    assertThat(thrown).hasMessageThat().isEqualTo("change is new.");
  }

  @Test
  public void revertSubmissionWithDependantChange() throws Exception {
    PushOneCommit.Result firstResult = createChange("first change", "a.txt", "message");
    PushOneCommit.Result secondResult = createChange("second change", "b.txt", "other");
    approve(secondResult.getChangeId());
    approve(firstResult.getChangeId());
    gApi.changes().id(secondResult.getChangeId()).current().submit();
    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(firstResult.getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);

    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    Collections.reverse(revertChanges);
    String sha1SecondChange = secondResult.getCommit().getName();
    String sha1FirstRevert = revertChanges.get(0).current().commit(false).commit;
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondChange);
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstRevert);
    assertThat(revertChanges.get(0).get().revertOf)
        .isEqualTo(secondResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(1).get().revertOf)
        .isEqualTo(firstResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(0).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("a.txt").linesDeleted).isEqualTo(1);

    assertThat(revertChanges).hasSize(2);
    assertThat(gApi.changes().id(revertChanges.get(0).id()).current().related().changes).hasSize(2);
  }

  @Test
  public void revertSubmissionWithDependantChangeWithoutRevertingLastOne() throws Exception {
    PushOneCommit.Result firstResult = createChange("first change", "a.txt", "message");
    PushOneCommit.Result secondResult = createChange("second change", "b.txt", "other");
    approve(secondResult.getChangeId());
    approve(firstResult.getChangeId());
    gApi.changes().id(secondResult.getChangeId()).current().submit();
    String unrelated = createChange("other change", "c.txt", "message other").getChangeId();
    approve(unrelated);
    gApi.changes().id(unrelated).current().submit();
    List<ChangeApi> revertChanges =
        getChangeApis(gApi.changes().id(firstResult.getChangeId()).revertSubmission());
    Collections.reverse(revertChanges);
    String sha1SecondChange = secondResult.getCommit().getName();
    String sha1FirstRevert = revertChanges.get(0).current().commit(false).commit;
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondChange);
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstRevert);
    assertThat(revertChanges.get(0).get().revertOf)
        .isEqualTo(secondResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(1).get().revertOf)
        .isEqualTo(firstResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(0).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("a.txt").linesDeleted).isEqualTo(1);

    assertThat(revertChanges).hasSize(2);
    assertThat(gApi.changes().id(revertChanges.get(0).id()).current().related().changes).hasSize(2);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionDifferentRepositories() throws Exception {
    projectOperations.newProject().name("secondProject").create();
    TestRepository<InMemoryRepository> secondRepo =
        cloneProject(Project.nameKey("secondProject"), admin);
    String topic = "topic";
    PushOneCommit.Result firstResult =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    PushOneCommit.Result secondResult =
        createChange(secondRepo, "master", "second change", "b.txt", "other", topic);
    approve(secondResult.getChangeId());
    approve(firstResult.getChangeId());
    // submit both changes
    gApi.changes().id(secondResult.getChangeId()).current().submit();
    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(secondResult.getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);

    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    // has size 2 because of the same topic, and submitWholeTopic is true.
    assertThat(gApi.changes().id(revertChanges.get(0).get()._number).submittedTogether())
        .hasSize(2);
    String sha1SecondChange = secondResult.getCommit().getName();
    String sha1FirstChange = firstResult.getCommit().getName();
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstChange);
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondChange);
    assertThat(revertChanges.get(0).get().revertOf)
        .isEqualTo(firstResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(1).get().revertOf)
        .isEqualTo(secondResult.getChange().change().getChangeId());
    assertThat(revertChanges.get(0).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("b.txt").linesDeleted).isEqualTo(1);

    assertThat(revertChanges).hasSize(2);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionMultipleBranches() throws Exception {
    List<PushOneCommit.Result> resultCommits = new ArrayList<>();
    String topic = "topic";
    resultCommits.add(createChange(testRepo, "master", "first change", "c.txt", "message", topic));
    testRepo.reset("HEAD~1");
    createBranch(BranchNameKey.create(project, "other"));
    resultCommits.add(createChange(testRepo, "other", "second change", "a.txt", "message", topic));
    resultCommits.add(
        createChange(testRepo, "other", "third change", "b.txt", "Other message", topic));
    for (PushOneCommit.Result result : resultCommits) {
      approve(result.getChangeId());
    }
    // submit all changes
    gApi.changes().id(resultCommits.get(1).getChangeId()).current().submit();
    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(resultCommits.get(1).getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);
    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    assertThat(revertChanges.get(0).current().files().get("c.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(2).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    String sha1FirstChange = resultCommits.get(0).getCommit().getName();
    String sha1ThirdChange = resultCommits.get(2).getCommit().getName();
    String sha1SecondRevert = revertChanges.get(2).current().commit(false).commit;
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstChange);
    assertThat(revertChanges.get(2).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1ThirdChange);
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondRevert);

    assertThat(revertChanges).hasSize(3);
    assertThat(gApi.changes().id(revertChanges.get(1).id()).current().related().changes).hasSize(2);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionDependantAndUnrelatedWithMerge() throws Exception {
    String topic = "topic";
    PushOneCommit.Result firstResult =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    approve(firstResult.getChangeId());
    PushOneCommit.Result secondResult =
        createChange(testRepo, "master", "second change", "b.txt", "message", topic);
    approve(secondResult.getChangeId());
    testRepo.reset("HEAD~1");
    PushOneCommit.Result thirdResult =
        createChange(testRepo, "master", "third change", "c.txt", "message", topic);
    approve(thirdResult.getChangeId());

    gApi.changes().id(firstResult.getChangeId()).current().submit();

    // put the head on the merge commit created by submitting the second and third change.
    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/master:merge")).call();
    testRepo.reset("merge");

    // Create another change that should be ignored. The reverts should be rebased on top of the
    // merge commit.
    PushOneCommit.Result fourthResult =
        createChange(testRepo, "master", "fourth change", "d.txt", "message", topic);
    approve(fourthResult.getChangeId());
    gApi.changes().id(fourthResult.getChangeId()).current().submit();

    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(secondResult.getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);
    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    Collections.reverse(revertChanges);
    assertThat(revertChanges.get(0).current().files().get("c.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(2).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    String sha1FirstRevert = revertChanges.get(0).current().commit(false).commit;
    String sha1SecondRevert = revertChanges.get(1).current().commit(false).commit;
    // parent of the first revert is the merged change of previous changes.
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).subject)
        .contains("Merge");
    // Next reverts would stack on top of the previous ones.
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstRevert);
    assertThat(revertChanges.get(2).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondRevert);

    assertThat(revertChanges).hasSize(3);
    assertThat(gApi.changes().id(revertChanges.get(1).id()).current().related().changes).hasSize(3);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionUnrelatedWithTwoMergeCommits() throws Exception {
    String topic = "topic";
    PushOneCommit.Result firstResult =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    approve(firstResult.getChangeId());
    testRepo.reset("HEAD~1");
    PushOneCommit.Result secondResult =
        createChange(testRepo, "master", "second change", "b.txt", "message", topic);
    approve(secondResult.getChangeId());
    testRepo.reset("HEAD~1");
    PushOneCommit.Result thirdResult =
        createChange(testRepo, "master", "third change", "c.txt", "message", topic);
    approve(thirdResult.getChangeId());

    gApi.changes().id(firstResult.getChangeId()).current().submit();

    // put the head on the most recent merge commit.
    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/master:merge")).call();
    testRepo.reset("merge");

    // Create another change that should be ignored. The reverts should be rebased on top of the
    // merge commit.
    PushOneCommit.Result fourthResult =
        createChange(testRepo, "master", "fourth change", "d.txt", "message", topic);
    approve(fourthResult.getChangeId());
    gApi.changes().id(fourthResult.getChangeId()).current().submit();

    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(secondResult.getChangeId()).revertSubmission();
    assertThat(
            revertSubmissionInfo.revertChanges.stream()
                .map(change -> change.created)
                .distinct()
                .count())
        .isEqualTo(1);
    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    Collections.reverse(revertChanges);
    assertThat(revertChanges.get(0).current().files().get("c.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(2).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    String sha1FirstRevert = revertChanges.get(0).current().commit(false).commit;
    String sha1SecondRevert = revertChanges.get(1).current().commit(false).commit;
    // parent of the first revert is the merged change of previous changes.
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).subject)
        .contains("Merge \"third change\"");
    // Next reverts would stack on top of the previous ones.
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstRevert);
    assertThat(revertChanges.get(2).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1SecondRevert);

    assertThat(revertChanges).hasSize(3);
    assertThat(gApi.changes().id(revertChanges.get(1).id()).current().related().changes).hasSize(3);
  }

  @Test
  @GerritConfig(name = "change.submitWholeTopic", value = "true")
  public void revertSubmissionUnrelatedWithAnotherDependantChangeWithDifferentTopic()
      throws Exception {
    String topic = "topic";
    PushOneCommit.Result firstResult =
        createChange(testRepo, "master", "first change", "a.txt", "message", topic);
    approve(firstResult.getChangeId());
    testRepo.reset("HEAD~1");
    PushOneCommit.Result secondResult =
        createChange(testRepo, "master", "second change", "b.txt", "message", topic);
    approve(secondResult.getChangeId());

    // A non-merged change without the same topic that is related to the second change.
    createChange();

    gApi.changes().id(firstResult.getChangeId()).current().submit();

    RevertSubmissionInfo revertSubmissionInfo =
        gApi.changes().id(secondResult.getChangeId()).revertSubmission();

    List<ChangeApi> revertChanges = getChangeApis(revertSubmissionInfo);
    Collections.reverse(revertChanges);
    assertThat(revertChanges.get(0).current().files().get("b.txt").linesDeleted).isEqualTo(1);
    assertThat(revertChanges.get(1).current().files().get("a.txt").linesDeleted).isEqualTo(1);
    // The parent of the first revert is the merge change of the submission.
    assertThat(revertChanges.get(0).current().commit(false).parents.get(0).subject)
        .contains("Merge \"second change\"");
    // Next revert would base itself on the previous revert.
    String sha1FirstRevert = revertChanges.get(0).current().commit(false).commit;
    assertThat(revertChanges.get(1).current().commit(false).parents.get(0).commit)
        .isEqualTo(sha1FirstRevert);

    assertThat(revertChanges).hasSize(2);
  }

  @Test
  public void revertSubmissionSubjects() throws Exception {
    String firstResult = createChange("first change", "a.txt", "message").getChangeId();
    String secondResult = createChange("second change", "b.txt", "other").getChangeId();
    approve(firstResult);
    approve(secondResult);
    gApi.changes().id(secondResult).current().submit();

    List<ChangeApi> firstRevertChanges =
        getChangeApis(gApi.changes().id(firstResult).revertSubmission());
    assertThat(firstRevertChanges.get(0).get().subject).isEqualTo("Revert \"first change\"");
    assertThat(firstRevertChanges.get(1).get().subject).isEqualTo("Revert \"second change\"");
    approve(firstRevertChanges.get(0).id());
    approve(firstRevertChanges.get(1).id());
    gApi.changes().id(firstRevertChanges.get(0).id()).current().submit();

    List<ChangeApi> secondRevertChanges =
        getChangeApis(gApi.changes().id(firstRevertChanges.get(0).id()).revertSubmission());
    assertThat(secondRevertChanges.get(0).get().subject).isEqualTo("Revert^2 \"second change\"");
    assertThat(secondRevertChanges.get(1).get().subject).isEqualTo("Revert^2 \"first change\"");
    approve(secondRevertChanges.get(0).id());
    approve(secondRevertChanges.get(1).id());
    gApi.changes().id(secondRevertChanges.get(0).id()).current().submit();

    List<ChangeApi> thirdRevertChanges =
        getChangeApis(gApi.changes().id(secondRevertChanges.get(0).id()).revertSubmission());
    assertThat(thirdRevertChanges.get(0).get().subject).isEqualTo("Revert^3 \"first change\"");
    assertThat(thirdRevertChanges.get(1).get().subject).isEqualTo("Revert^3 \"second change\"");
  }

  @Test
  public void revertSubmissionWithUserChangedSubjects() throws Exception {
    String firstResult = createChange("Revert^aa", "a.txt", "message").getChangeId();
    String secondResult = createChange("Revert", "b.txt", "other").getChangeId();
    String thirdResult = createChange("Revert^934 \"change x\"", "c.txt", "another").getChangeId();
    String fourthResult = createChange("Revert^934", "d.txt", "last").getChangeId();
    approve(firstResult);
    approve(secondResult);
    approve(thirdResult);
    approve(fourthResult);
    gApi.changes().id(fourthResult).current().submit();

    List<ChangeApi> firstRevertChanges =
        getChangeApis(gApi.changes().id(firstResult).revertSubmission());
    assertThat(firstRevertChanges.get(0).get().subject).isEqualTo("Revert \"Revert^aa\"");
    assertThat(firstRevertChanges.get(1).get().subject).isEqualTo("Revert \"Revert\"");
    assertThat(firstRevertChanges.get(2).get().subject).isEqualTo("Revert^935 \"change x\"");
    assertThat(firstRevertChanges.get(3).get().subject).isEqualTo("Revert \"Revert^934\"");
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
    projectCache.evict(project);
  }

  private List<ChangeApi> getChangeApis(RevertSubmissionInfo revertSubmissionInfo)
      throws Exception {
    List<ChangeApi> results = new ArrayList<>();
    for (ChangeInfo changeInfo : revertSubmissionInfo.revertChanges) {
      results.add(gApi.changes().id(changeInfo._number));
    }
    return results;
  }
}
