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
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.RefNames;
import org.junit.Before;
import org.junit.Test;

public class DeleteBranchIT extends AbstractDaemonTest {

  private Branch.NameKey testBranch;

  @Before
  public void setUp() throws Exception {
    project = createProject(name("p"));
    testBranch = new Branch.NameKey(project, "test");
    branch(testBranch).create(new BranchInput());
  }

  @Test
  public void deleteBranch_Forbidden() throws Exception {
    setApiUser(user);
    assertDeleteForbidden(testBranch);
  }

  @Test
  public void deleteBranchByAdmin() throws Exception {
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
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
    setApiUser(user);
    assertDeleteForbidden(testBranch);
  }

  @Test
  public void deleteBranchByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    setApiUser(user);
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByUserWithDeletePermission() throws Exception {
    grantDelete();
    setApiUser(user);
    assertDeleteSucceeds(testBranch);
  }

  @Test
  public void deleteBranchByRestWithoutRefsHeadsPrefix() throws Exception {
    grantDelete();
    String ref = testBranch.getShortName();
    assertThat(ref).doesNotMatch(R_HEADS);
    assertDeleteByRestSucceeds(testBranch, ref);
  }

  @Test
  public void deleteBranchByRestWithFullName() throws Exception {
    grantDelete();
    assertDeleteByRestSucceeds(testBranch, testBranch.get());
  }

  @Test
  public void deleteBranchByRestFailsWithUnencodedFullName() throws Exception {
    grantDelete();
    RestResponse r =
        userRestSession.delete("/projects/" + project.get() + "/branches/" + testBranch.get());
    r.assertNotFound();
    branch(testBranch).get();
  }

  @Test
  public void deleteMetaBranch() throws Exception {
    String metaRef = RefNames.REFS_META + "foo";
    allow(metaRef, Permission.CREATE, REGISTERED_USERS);
    allow(metaRef, Permission.PUSH, REGISTERED_USERS);

    Branch.NameKey metaBranch = new Branch.NameKey(project, metaRef);
    branch(metaBranch).create(new BranchInput());

    grantDelete();
    assertDeleteByRestSucceeds(metaBranch, metaRef);
  }

  @Test
  public void deleteUserBranch_Conflict() throws Exception {
    allow(allUsers, RefNames.REFS_USERS + "*", Permission.CREATE, REGISTERED_USERS);
    allow(allUsers, RefNames.REFS_USERS + "*", Permission.PUSH, REGISTERED_USERS);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Not allowed to delete user branch.");
    branch(new Branch.NameKey(allUsers, RefNames.refsUsers(admin.id))).delete();
  }

  @Test
  @GerritConfig(name = "noteDb.groups.write", value = "true")
  public void deleteGroupBranch_Conflict() throws Exception {
    allow(allUsers, RefNames.REFS_GROUPS + "*", Permission.CREATE, REGISTERED_USERS);
    allow(allUsers, RefNames.REFS_GROUPS + "*", Permission.PUSH, REGISTERED_USERS);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Not allowed to delete group branch.");
    branch(new Branch.NameKey(allUsers, RefNames.refsGroups(adminGroupUuid()))).delete();
  }

  private void blockForcePush() throws Exception {
    block("refs/heads/*", Permission.PUSH, ANONYMOUS_USERS).setForce(true);
  }

  private void grantForcePush() throws Exception {
    grant(project, "refs/heads/*", Permission.PUSH, true, ANONYMOUS_USERS);
  }

  private void grantDelete() throws Exception {
    allow("refs/*", Permission.DELETE, ANONYMOUS_USERS);
  }

  private void grantOwner() throws Exception {
    allow("refs/*", Permission.OWNER, REGISTERED_USERS);
  }

  private BranchApi branch(Branch.NameKey branch) throws Exception {
    return gApi.projects().name(branch.getParentKey().get()).branch(branch.get());
  }

  private void assertDeleteByRestSucceeds(Branch.NameKey branch, String ref) throws Exception {
    RestResponse r =
        userRestSession.delete(
            "/projects/"
                + IdString.fromDecoded(project.get()).encoded()
                + "/branches/"
                + IdString.fromDecoded(ref).encoded());
    r.assertNoContent();
    exception.expect(ResourceNotFoundException.class);
    branch(branch).get();
  }

  private void assertDeleteSucceeds(Branch.NameKey branch) throws Exception {
    assertThat(branch(branch).get().canDelete).isTrue();
    String branchRev = branch(branch).get().revision;
    branch(branch).delete();
    eventRecorder.assertRefUpdatedEvents(
        project.get(), branch.get(), null, branchRev, branchRev, null);
    exception.expect(ResourceNotFoundException.class);
    branch(branch).get();
  }

  private void assertDeleteForbidden(Branch.NameKey branch) throws Exception {
    assertThat(branch(branch).get().canDelete).isNull();
    exception.expect(AuthException.class);
    exception.expectMessage("not permitted: delete");
    branch(branch).delete();
  }
}
