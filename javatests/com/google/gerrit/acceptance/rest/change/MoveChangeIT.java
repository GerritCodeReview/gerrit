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
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.LabelFunction;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.MoveInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class MoveChangeIT extends AbstractDaemonTest {
  @Test
  public void moveChangeWithShortRef() throws Exception {
    // Move change to a different branch using short ref name
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    move(r.getChangeId(), newBranch.getShortName());
    assertThat(r.getChange().change().getDest()).isEqualTo(newBranch);
  }

  @Test
  public void moveChangeWithFullRef() throws Exception {
    // Move change to a different branch using full ref name
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    move(r.getChangeId(), newBranch.get());
    assertThat(r.getChange().change().getDest()).isEqualTo(newBranch);
  }

  @Test
  public void moveChangeWithMessage() throws Exception {
    // Provide a message using --message flag
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    String moveMessage = "Moving for the move test";
    move(r.getChangeId(), newBranch.get(), moveMessage);
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
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is already destined for the specified branch");
    move(r.getChangeId(), r.getChange().change().getDest().get());
  }

  @Test
  public void moveChangeToSameChangeId() throws Exception {
    // Move change to a branch with existing change with same change ID
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    int changeNum = r.getChange().change().getChangeId();
    createChange(newBranch.get(), r.getChangeId());
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        "Destination "
            + newBranch.getShortName()
            + " has a different change with same change key "
            + r.getChangeId());
    move(changeNum, newBranch.get());
  }

  @Test
  public void moveChangeToNonExistentRef() throws Exception {
    // Move change to a non-existing branch
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch =
        new Branch.NameKey(r.getChange().change().getProject(), "does_not_exist");
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Destination " + newBranch.get() + " not found in the project");
    move(r.getChangeId(), newBranch.get());
  }

  @Test
  public void moveClosedChange() throws Exception {
    // Move a change which is not open
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    merge(r);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is merged");
    move(r.getChangeId(), newBranch.get());
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
        .author(admin.getIdent())
        .committer(new PersonIdent(admin.getIdent(), testRepo.getDate()));
    RevCommit c = commitBuilder.create();
    pushHead(testRepo, "refs/for/master", false, false);

    // Try to move the merge commit to another branch
    Branch.NameKey newBranch = new Branch.NameKey(r1.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Merge commit cannot be moved");
    move(GitUtil.getChangeId(testRepo, c).get(), newBranch.get());
  }

  @Test
  public void moveChangeToBranchWithoutUploadPerms() throws Exception {
    // Move change to a destination where user doesn't have upload permissions
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch =
        new Branch.NameKey(r.getChange().change().getProject(), "blocked_branch");
    createBranch(newBranch);
    block(
        "refs/for/" + newBranch.get(),
        Permission.PUSH,
        systemGroupBackend.getGroup(REGISTERED_USERS).getUUID());
    exception.expect(AuthException.class);
    exception.expectMessage("move not permitted");
    move(r.getChangeId(), newBranch.get());
  }

  @Test
  public void moveChangeFromBranchWithoutAbandonPerms() throws Exception {
    // Move change for which user does not have abandon permissions
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);
    block(
        r.getChange().change().getDest().get(),
        Permission.ABANDON,
        systemGroupBackend.getGroup(REGISTERED_USERS).getUUID());
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("move not permitted");
    move(r.getChangeId(), newBranch.get());
  }

  @Test
  public void moveChangeToBranchThatContainsCurrentCommit() throws Exception {
    // Move change to a branch for which current PS revision is reachable from
    // tip

    // Create a change
    PushOneCommit.Result r = createChange();
    int changeNum = r.getChange().change().getChangeId();

    // Create a branch with that same commit
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    BranchInput bi = new BranchInput();
    bi.revision = r.getCommit().name();
    gApi.projects().name(newBranch.getParentKey().get()).branch(newBranch.get()).create(bi);

    // Try to move the change to the branch with the same commit
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        "Current patchset revision is reachable from tip of " + newBranch.get());
    move(changeNum, newBranch.get());
  }

  @Test
  public void moveChangeWithCurrentPatchSetLocked() throws Exception {
    // Move change that is locked
    PushOneCommit.Result r = createChange();
    Branch.NameKey newBranch = new Branch.NameKey(r.getChange().change().getProject(), "moveTest");
    createBranch(newBranch);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType patchSetLock = Util.patchSetLock();
    cfg.getLabelSections().put(patchSetLock.getName(), patchSetLock);
    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    Util.allow(
        cfg, Permission.forLabel(patchSetLock.getName()), 0, 1, registeredUsers, "refs/heads/*");
    saveProjectConfig(cfg);
    grant(project, "refs/heads/*", Permission.LABEL + "Patch-Set-Lock");
    revision(r).review(new ReviewInput().label("Patch-Set-Lock", 1));

    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format("The current patch set of change %s is locked", r.getChange().getId()));
    move(r.getChangeId(), newBranch.get());
  }

  @Test
  public void moveChangeOnlyKeepVetoVotes() throws Exception {
    // A vote for a label will be kept after moving if the label's function is *WithBlock and the
    // vote holds the minimum value.
    createBranch(new Branch.NameKey(project, "foo"));

    String codeReviewLabel = "Code-Review"; // 'Code-Review' uses 'MaxWithBlock' function.
    String testLabelA = "Label-A";
    String testLabelB = "Label-B";
    String testLabelC = "Label-C";
    configLabel(testLabelA, LabelFunction.ANY_WITH_BLOCK);
    configLabel(testLabelB, LabelFunction.MAX_NO_BLOCK);
    configLabel(testLabelC, LabelFunction.NO_BLOCK);

    AccountGroup.UUID registered = SystemGroupBackend.REGISTERED_USERS;
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg, Permission.forLabel(testLabelA), -1, +1, registered, "refs/heads/*");
    Util.allow(cfg, Permission.forLabel(testLabelB), -1, +1, registered, "refs/heads/*");
    Util.allow(cfg, Permission.forLabel(testLabelC), -1, +1, registered, "refs/heads/*");
    saveProjectConfig(cfg);

    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.reject());

    amendChange(changeId);

    ReviewInput input = new ReviewInput();
    input.label(testLabelA, -1);
    input.label(testLabelB, -1);
    input.label(testLabelC, -1);
    gApi.changes().id(changeId).current().review(input);

    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email).votes().keySet())
        .containsExactly(codeReviewLabel, testLabelA, testLabelB, testLabelC);
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email).votes().values())
        .containsExactly((short) -2, (short) -1, (short) -1, (short) -1);

    // Move the change to the 'foo' branch.
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("master");
    move(changeId, "foo");
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("foo");

    // 'Code-Review -2' and 'Label-A -1' will be kept.
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email).votes().values())
        .containsExactly((short) -2, (short) -1, (short) 0, (short) 0);

    // Move the change back to 'master'.
    move(changeId, "master");
    assertThat(gApi.changes().id(changeId).get().branch).isEqualTo("master");
    assertThat(gApi.changes().id(changeId).current().reviewer(admin.email).votes().values())
        .containsExactly((short) -2, (short) -1, (short) 0, (short) 0);
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
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo, changeId);
    PushOneCommit.Result result = push.to("refs/for/" + branch);
    result.assertOkStatus();
    return result;
  }
}
