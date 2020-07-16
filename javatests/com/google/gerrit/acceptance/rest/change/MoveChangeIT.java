// Copyright (C) 2015 The Android Open Source Project
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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.Arrays;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class MoveChangeIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void moveChangeWithShortRef() throws Exception {
    // Move change to a different branch using short ref name
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    move(r.getChangeId(), newBranch.shortName());
    assertThat(r.getChange().change().getDest()).isEqualTo(newBranch);
  }

  @Test
  public void moveChangeWithFullRef() throws Exception {
    // Move change to a different branch using full ref name
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    move(r.getChangeId(), newBranch.branch());
    assertThat(r.getChange().change().getDest()).isEqualTo(newBranch);
  }

  @Test
  public void moveChangeWithMessage() throws Exception {
    // Provide a message using --message flag
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    String moveMessage = "Moving for the move test";
    move(r.getChangeId(), newBranch.branch(), moveMessage);
    assertThat(r.getChange().change().getDest()).isEqualTo(newBranch);
    StringBuilder expectedMessage = new StringBuilder();
    expectedMessage.append("Change destination moved from master to moveTest");
    expectedMessage.append("\n\n");
    expectedMessage.append(moveMessage);
    assertThat(r.getChange().messages().get(1).getMessage()).isEqualTo(expectedMessage.toString());
  }

  @Test
  public void moveChangeToSameRefAsCurrent() throws Exception {
    // Move change to the branch same as change's destination
    PushOneCommit.Result r = createChange();
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> move(r.getChangeId(), r.getChange().change().getDest().branch()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Change is already destined for the specified branch");
  }

  @Test
  public void moveChangeToSameChangeId() throws Exception {
    // Move change to a branch with existing change with same change ID
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    int changeNum = r.getChange().change().getChangeId();
    createChange(newBranch.branch(), r.getChangeId());
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> move(changeNum, newBranch.branch()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Destination "
                + newBranch.shortName()
                + " has a different change with same change key "
                + r.getChangeId());
  }

  @Test
  public void moveChangeToNonExistentRef() throws Exception {
    // Move change to a non-existing branch
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch =
        BranchNameKey.create(r.getChange().change().getProject(), "does_not_exist");
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> move(r.getChangeId(), newBranch.branch()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Destination " + newBranch.branch() + " not found in the project");
  }

  @Test
  public void moveClosedChange() throws Exception {
    // Move a change which is not open
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    merge(r);
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> move(r.getChangeId(), newBranch.branch()));
    assertThat(thrown).hasMessageThat().contains("Change is merged");
  }

  @Test
  public void moveMergeCommitChange() throws Exception {
    // Move a change which has a merge commit as the current PS
    // Create a merge commit and push for review
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    TestRepository<?>.CommitBuilder commitBuilder =
        testRepo.branch("HEAD").commit().insertChangeId();
    commitBuilder
        .parent(r1.getCommit())
        .parent(r2.getCommit())
        .message("Move change Merge Commit")
        .author(admin.newIdent())
        .committer(new PersonIdent(admin.newIdent(), testRepo.getDate()));
    RevCommit c = commitBuilder.create();
    pushHead(testRepo, "refs/for/master", false, false);

    // Try to move the merge commit to another branch
    BranchNameKey newBranch =
        BranchNameKey.create(r1.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> move(GitUtil.getChangeId(testRepo, c).get(), newBranch.branch()));
    assertThat(thrown).hasMessageThat().contains("Merge commit cannot be moved");
  }

  @Test
  public void moveChangeToBranchWithoutUploadPerms() throws Exception {
    // Move change to a destination where user doesn't have upload permissions
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch =
        BranchNameKey.create(r.getChange().change().getProject(), "blocked_branch");
    createBranch(newBranch);
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/for/" + newBranch.branch()).group(REGISTERED_USERS))
        .update();
    AuthException thrown =
        assertThrows(AuthException.class, () -> move(r.getChangeId(), newBranch.branch()));
    assertThat(thrown).hasMessageThat().contains("move not permitted");
  }

  @Test
  public void moveChangeFromBranchWithoutAbandonPerms() throws Exception {
    // Move change for which user does not have abandon permissions
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            block(Permission.ABANDON)
                .ref(r.getChange().change().getDest().branch())
                .group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> move(r.getChangeId(), newBranch.branch()));
    assertThat(thrown).hasMessageThat().contains("move not permitted");
  }

  @Test
  public void moveChangeToBranchThatContainsCurrentCommit() throws Exception {
    // Move change to a branch for which current PS revision is reachable from
    // tip

    // Create a change
    PushOneCommit.Result r = createChange();
    int changeNum = r.getChange().change().getChangeId();

    // Create a branch with that same commit
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    BranchInput bi = new BranchInput();
    bi.revision = r.getCommit().name();
    gApi.projects().name(newBranch.project().get()).branch(newBranch.branch()).create(bi);

    // Try to move the change to the branch with the same commit
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> move(changeNum, newBranch.branch()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Current patchset revision is reachable from tip of " + newBranch.branch());
  }

  @Test
  public void moveChangeWithCurrentPatchSetLocked() throws Exception {
    // Move change that is locked
    PushOneCommit.Result r = createChange();
    BranchNameKey newBranch = BranchNameKey.create(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);

    LabelType patchSetLock = TestLabels.patchSetLock();
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().upsertLabelType(patchSetLock);
      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(patchSetLock.getName())
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(0, 1))
        .update();
    revision(r).review(new ReviewInput().label("Patch-Set-Lock", 1));

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class, () -> move(r.getChangeId(), newBranch.branch()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            String.format("The current patch set of change %s is locked", r.getChange().getId()));
  }

  @Test
  public void moveChangeOnlyKeepVetoVotes() throws Exception {
    // A vote for a label will be kept after moving if the label's function is *WithBlock and the
    // vote holds the minimum value.
    createBranch(BranchNameKey.create(project, "foo"));

    String codeReviewLabel = "Code-Review"; // 'Code-Review' uses 'MaxWithBlock' function.
    String testLabelA = "Label-A";
    String testLabelB = "Label-B";
    String testLabelC = "Label-C";
    configLabel(testLabelA, LabelFunction.ANY_WITH_BLOCK);
    configLabel(testLabelB, LabelFunction.MAX_NO_BLOCK);
    configLabel(testLabelC, LabelFunction.NO_BLOCK);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(testLabelA).ref("refs/heads/*").group(REGISTERED_USERS).range(-1, +1))
        .add(allowLabel(testLabelB).ref("refs/heads/*").group(REGISTERED_USERS).range(-1, +1))
        .add(allowLabel(testLabelC).ref("refs/heads/*").group(REGISTERED_USERS).range(-1, +1))
        .update();

    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.reject());

    amendChange(changeId);

    ReviewInput input = new ReviewInput();
    input.label(testLabelA, -1);
    input.label(testLabelB, -1);
    input.label(testLabelC, -1);
    gApi.changes().id(changeId).current().review(input);

    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().keySet())
        .containsExactly(codeReviewLabel, testLabelA, testLabelB, testLabelC);
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().values())
        .containsExactly((short) -2, (short) -1, (short) -1, (short) -1);

    // Move the change to the 'foo' branch.
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("master");
    move(changeId, "foo");
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("foo");

    // 'Code-Review -2' and 'Label-A -1' will be kept.
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().values())
        .containsExactly((short) -2, (short) -1, (short) 0, (short) 0);

    // Move the change back to 'master'.
    move(changeId, "master");
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("master");
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().values())
        .containsExactly((short) -2, (short) -1, (short) 0, (short) 0);
  }

  @Test
  public void moveToBranchThatDoesNotHaveCustomLabel() throws Exception {
    createBranch(BranchNameKey.create(project, "foo"));
    String testLabelA = "Label-A";
    configLabel(testLabelA, LabelFunction.MAX_WITH_BLOCK, Arrays.asList("refs/heads/master"));

    projectOperations
        .project(project)
        .forUpdate()
        .add(allowLabel(testLabelA).ref("refs/heads/master").group(REGISTERED_USERS).range(-1, +1))
        .update();

    String changeId = createChange().getChangeId();

    ReviewInput input = new ReviewInput();
    input.label(testLabelA, -1);
    gApi.changes().id(changeId).current().review(input);

    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().keySet())
        .containsExactly(testLabelA);
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().values())
        .containsExactly((short) -1);

    move(changeId, "foo");

    // "foo" branch does not have the custom label
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().keySet())
        .isEmpty();

    // Move back to master and confirm that the custom label score is still there
    move(changeId, "master");

    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().keySet())
        .containsExactly(testLabelA);
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email()).votes().values())
        .containsExactly((short) -1);
  }

  @Test
  public void moveNoDestinationBranchSpecified() throws Exception {
    PushOneCommit.Result r = createChange();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> move(r.getChangeId(), null));
    assertThat(thrown).hasMessageThat().contains("destination branch is required");
  }

  @Test
  @GerritConfig(name = "change.move", value = "false")
  public void moveCanBeDisabledByConfig() throws Exception {
    PushOneCommit.Result r = createChange();

    MethodNotAllowedException thrown =
        assertThrows(MethodNotAllowedException.class, () -> move(r.getChangeId(), null));
    assertThat(thrown).hasMessageThat().contains("move changes endpoint is disabled");
  }

  private void move(int changeNum, String destination) throws RestApiException {
    gApi.changes().id(changeNum).move(destination);
  }

  private void move(String changeId, String destination) throws RestApiException {
    gApi.changes().id(changeId).move(destination);
  }

  private void move(String changeId, String destination, String message) throws RestApiException {
    MoveInput in = new MoveInput();
    in.destinationBranch = destination;
    in.message = message;
    gApi.changes().id(changeId).move(in);
  }

  private PushOneCommit.Result createChange(String branch, String changeId) throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, changeId);
    PushOneCommit.Result result = push.to("refs/for/" + branch);
    result.assertOkStatus();
    return result;
  }
}
