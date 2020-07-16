// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class DeleteBranchIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private BranchNameKey testBranch;

  @Before
  public void setUp() throws Exception {
    project = projectOperations.newProject().create();
    testBranch = BranchNameKey.create(project, "test");
    branch(testBranch).create(new BranchInput());
  }

  @Test
  public void deleteBranch_Forbidden() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertDeleteForbidden(testBranch);
  }

  @Test
  public void deleteBranchByAdmin() throws Exception {
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByProjectOwner() throws Exception {
    grantOwner();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByAdminForcePushBlocked() throws Exception {
    blockForcePush();
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByProjectOwnerForcePushBlocked_Forbidden() throws Exception {
    grantOwner();
    blockForcePush();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteForbidden(testBranch);
  }

  @Test
  public void deleteBranchByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByUserWithDeletePermission() throws Exception {
    grantDelete();
    requestScopeOperations.setApiUser(user.id());
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByRestWithoutRefsHeadsPrefix() throws Exception {
    grantDelete();
    String ref = testBranch.shortName();
    assertThat(ref).doesNotMatch(R_HEADS);
    assertDeleteByRestSucceeds(testBranch, ref);
  }

  @Test
  public void deleteBranchByRestWithFullName() throws Exception {
    grantDelete();
    assertDeleteByRestSucceeds(testBranch, testBranch.branch());
  }

  @Test
  public void deleteBranchByRestFailsWithUnencodedFullName() throws Exception {
    grantDelete();
    RestResponse r =
        userRestSession.delete("/projects/" + project.get() + "/branches/" + testBranch.branch());
    r.assertNotFound();
    branch(testBranch).get();
  }

  @Test
  public void deleteMetaBranch() throws Exception {
    String metaRef = RefNames.REFS_META + "foo";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(metaRef).group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(metaRef).group(REGISTERED_USERS))
        .update();

    BranchNameKey metaBranch = BranchNameKey.create(project, metaRef);
    branch(metaBranch).create(new BranchInput());

    grantDelete();
    assertDeleteByRestSucceeds(metaBranch, metaRef);
  }

  @Test
  public void deleteUserBranch_Conflict() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .update();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> branch(BranchNameKey.create(allUsers, RefNames.refsUsers(admin.id()))).delete());
    assertThat(thrown).hasMessageThat().contains("Not allowed to delete user branch.");
  }

  @Test
  public void deleteGroupBranch_Conflict() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .update();

    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () ->
                branch(BranchNameKey.create(allUsers, RefNames.refsGroups(adminGroupUuid())))
                    .delete());
    assertThat(thrown).hasMessageThat().contains("Not allowed to delete group branch.");
  }

  @Test
  public void cannotDeleteRefsMetaConfig() throws Exception {
    MethodNotAllowedException thrown =
        assertThrows(
            MethodNotAllowedException.class,
            () -> branch(BranchNameKey.create(allUsers, RefNames.REFS_CONFIG)).delete());
    assertThat(thrown).hasMessageThat().contains("not allowed to delete branch refs/meta/config");
  }

  @Test
  public void cannotDeleteHead() throws Exception {
    MethodNotAllowedException thrown =
        assertThrows(
            MethodNotAllowedException.class,
            () -> branch(BranchNameKey.create(allUsers, RefNames.HEAD)).delete());
    assertThat(thrown).hasMessageThat().contains("not allowed to delete HEAD");
  }

  private void blockForcePush() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS).force(true))
        .update();
  }

  private void grantForcePush() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(ANONYMOUS_USERS).force(true))
        .update();
  }

  private void grantDelete() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.DELETE).ref("refs/*").group(ANONYMOUS_USERS))
        .update();
  }

  private void grantOwner() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(REGISTERED_USERS))
        .update();
  }

  private BranchApi branch(BranchNameKey branch) throws Exception {
    return gApi.projects().name(branch.project().get()).branch(branch.branch());
  }

  private void assertDeleteByRestSucceeds(BranchNameKey branch, String ref) throws Exception {
    RestResponse r =
        userRestSession.delete(
            "/projects/"
                + IdString.fromDecoded(project.get()).encoded()
                + "/branches/"
                + IdString.fromDecoded(ref).encoded());
    r.assertNoContent();
    assertThrows(ResourceNotFoundException.class, () -> branch(branch).get());
  }

  private void assertDeleteSucceeds(BranchNameKey branch) throws Exception {
    assertThat(branch(branch).get().canDelete).isTrue();
    String branchRev = branch(branch).get().revision;
    branch(branch).delete();
    eventRecorder.assertRefUpdatedEvents(
        project.get(), branch.branch(), null, branchRev, branchRev, null);
    assertThrows(ResourceNotFoundException.class, () -> branch(branch).get());
  }

  private void assertDeleteForbidden(BranchNameKey branch) throws Exception {
    assertThat(branch(branch).get().canDelete).isNull();
    AuthException thrown = assertThrows(AuthException.class, () -> branch(branch).delete());
    assertThat(thrown).hasMessageThat().contains("not permitted: delete");
  }
}
