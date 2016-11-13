// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import org.eclipse.jgit.lib.Constants;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class CreateBranchIT extends AbstractDaemonTest {
  private Branch.NameKey branch;

  @Before
  public void setUp() throws Exception {
    branch = new Branch.NameKey(project, "test");
  }

  @Test
  public void createBranch_Forbidden() throws Exception {
    setApiUser(user);
    assertCreateFails(AuthException.class);
  }

  @Test
  public void createBranchByAdmin() throws Exception {
    assertCreateSucceeds();
  }

  @Test
  public void branchAlreadyExists_Conflict() throws Exception {
    assertCreateSucceeds();
    assertCreateFails(ResourceConflictException.class);
  }

  @Test
  public void createBranchByProjectOwner() throws Exception {
    grantOwner();
    setApiUser(user);
    assertCreateSucceeds();
  }

  @Test
  public void createBranchByAdminCreateReferenceBlocked_Forbidden() throws Exception {
    blockCreateReference();
    assertCreateFails(AuthException.class);
  }

  @Test
  public void createBranchByProjectOwnerCreateReferenceBlocked_Forbidden() throws Exception {
    grantOwner();
    blockCreateReference();
    setApiUser(user);
    assertCreateFails(AuthException.class);
  }

  private void blockCreateReference() throws Exception {
    block(Permission.CREATE, ANONYMOUS_USERS, "refs/*");
  }

  private void grantOwner() throws Exception {
    allow(Permission.OWNER, REGISTERED_USERS, "refs/*");
  }

  private BranchApi branch() throws Exception {
    return gApi.projects().name(branch.getParentKey().get()).branch(branch.get());
  }

  private void assertCreateSucceeds() throws Exception {
    BranchInfo created = branch().create(new BranchInput()).get();
    assertThat(created.ref).isEqualTo(Constants.R_HEADS + branch.getShortName());
  }

  private void assertCreateFails(Class<? extends RestApiException> errType) throws Exception {
    exception.expect(errType);
    branch().create(new BranchInput());
  }
}
