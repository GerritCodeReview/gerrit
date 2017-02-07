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

import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Branch;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class DeleteBranchIT extends AbstractDaemonTest {

  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    project = createProject(name("p"));
    branch = new Branch.NameKey(project, "test");
    branch().create(new BranchInput());
  }

  @Test
  public void deleteBranch_Forbidden() throws Exception {
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteBranchByAdmin() throws Exception {
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByAdminForcePushBlocked() throws Exception {
    blockForcePush();
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByProjectOwnerForcePushBlocked_Forbidden() throws Exception {
    grantOwner();
    blockForcePush();
    setApiUser(user);
    assertDeleteForbidden();
  }

  @Test
  public void deleteBranchByUserWithForcePushPermission() throws Exception {
    grantForcePush();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  @Test
  public void deleteBranchByUserWithDeletePermission() throws Exception {
    grantDelete();
    setApiUser(user);
    assertDeleteSucceeds();
  }

  private void blockForcePush() throws Exception {
    block(Permission.PUSH, ANONYMOUS_USERS, "refs/heads/*").setForce(true);
  }

  private void grantForcePush() throws Exception {
    grant(Permission.PUSH, project, "refs/heads/*", true, ANONYMOUS_USERS);
  }

  private void grantDelete() throws Exception {
    allow(Permission.DELETE, ANONYMOUS_USERS, "refs/*");
  }

  private void grantOwner() throws Exception {
    allow(Permission.OWNER, REGISTERED_USERS, "refs/*");
  }

  private BranchApi branch() throws Exception {
    return gApi.projects().name(branch.getParentKey().get()).branch(branch.get());
  }

  private void assertDeleteSucceeds() throws Exception {
    String branchRev = branch().get().revision;
    branch().delete();
    eventRecorder.assertRefUpdatedEvents(
        project.get(), branch.get(), null, branchRev, branchRev, null);
    exception.expect(ResourceNotFoundException.class);
    branch().get();
  }

  private void assertDeleteForbidden() throws Exception {
    exception.expect(AuthException.class);
    exception.expectMessage("Cannot delete branch");
    branch().delete();
  }
}
