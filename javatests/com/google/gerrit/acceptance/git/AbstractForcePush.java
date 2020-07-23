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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.deleteRef;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK;
import static org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

public abstract class AbstractForcePush extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void forcePushNotAllowed() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    PushOneCommit push1 =
        pushFactory.create(admin.newIdent(), testRepo, "change1", "a.txt", "content");
    PushOneCommit.Result r1 = push1.to("refs/heads/master");
    r1.assertOkStatus();

    // Reset HEAD to initial so the new change is a non-fast forward
    RefUpdate ru = repo().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(admin.newIdent(), testRepo, "change2", "b.txt", "content");
    push2.setForce(true);
    PushOneCommit.Result r2 = push2.to("refs/heads/master");
    r2.assertErrorStatus("not permitted: force update");
  }

  @Test
  public void forcePushAllowed() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(adminGroupUuid()).force(true))
        .update();
    PushOneCommit push1 =
        pushFactory.create(admin.newIdent(), testRepo, "change1", "a.txt", "content");
    PushOneCommit.Result r1 = push1.to("refs/heads/master");
    r1.assertOkStatus();

    // Reset HEAD to initial so the new change is a non-fast forward
    RefUpdate ru = repo().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(admin.newIdent(), testRepo, "change2", "b.txt", "content");
    push2.setForce(true);
    PushOneCommit.Result r2 = push2.to("refs/heads/master");
    r2.assertOkStatus();
  }

  @Test
  public void deleteNotAllowed() throws Exception {
    assertDeleteRef(REJECTED_OTHER_REASON);
  }

  @Test
  public void deleteNotAllowedWithOnlyPushPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(adminGroupUuid()))
        .update();
    assertDeleteRef(REJECTED_OTHER_REASON);
  }

  @Test
  public void deleteAllowedWithForcePushPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/*").group(adminGroupUuid()).force(true))
        .update();
    assertDeleteRef(OK);
  }

  @Test
  public void deleteAllowedWithDeletePermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/*").group(adminGroupUuid()).force(true))
        .update();
    assertDeleteRef(OK);
  }

  private void assertDeleteRef(RemoteRefUpdate.Status expectedStatus) throws Exception {
    BranchInput in = new BranchInput();
    in.ref = "refs/heads/test";
    gApi.projects().name(project.get()).branch(in.ref).create(in);
    PushResult result = deleteRef(testRepo, in.ref);
    RemoteRefUpdate refUpdate = result.getRemoteUpdate(in.ref);
    assertThat(refUpdate.getStatus()).isEqualTo(expectedStatus);
  }
}
