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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.entities.RefNames.REFS_HEADS;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.BranchApi;
import com.google.gerrit.extensions.api.projects.BranchInfo;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class CreateBranchIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private BranchNameKey testBranch;

  @Before
  public void setUp() throws Exception {
    testBranch = BranchNameKey.create(project, "test");
  }

  @Test
  public void createBranchRestApi() throws Exception {
    BranchInput input = new BranchInput();
    input.ref = "foo";
    assertThat(gApi.projects().name(project.get()).branches().get().stream().map(i -> i.ref))
        .doesNotContain(REFS_HEADS + input.ref);
    RestResponse r =
        adminRestSession.put("/projects/" + project.get() + "/branches/" + input.ref, input);
    r.assertCreated();
    assertThat(gApi.projects().name(project.get()).branches().get().stream().map(i -> i.ref))
        .contains(REFS_HEADS + input.ref);
  }

  @Test
  public void createBranch_Forbidden() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createBranchByAdmin() throws Exception {
    assertCreateSucceeds(testBranch);
  }

  @Test
  public void branchAlreadyExists_Conflict() throws Exception {
    assertCreateSucceeds(testBranch);
    assertCreateFails(testBranch, ResourceConflictException.class);
  }

  @Test
  public void createBranchByProjectOwner() throws Exception {
    grantOwner();
    requestScopeOperations.setApiUser(user.id());
    assertCreateSucceeds(testBranch);
  }

  @Test
  public void createBranchByAdminCreateReferenceBlocked_Forbidden() throws Exception {
    blockCreateReference();
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createBranchByProjectOwnerCreateReferenceBlocked_Forbidden() throws Exception {
    grantOwner();
    blockCreateReference();
    requestScopeOperations.setApiUser(user.id());
    assertCreateFails(testBranch, AuthException.class, "not permitted: create on refs/heads/test");
  }

  @Test
  public void createMetaBranch() throws Exception {
    String metaRef = RefNames.REFS_META + "foo";
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(metaRef).group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(metaRef).group(REGISTERED_USERS))
        .update();
    assertCreateSucceeds(BranchNameKey.create(project, metaRef));
  }

  @Test
  public void createUserBranch_Conflict() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_USERS + "*").group(REGISTERED_USERS))
        .update();
    assertCreateFails(
        BranchNameKey.create(allUsers, RefNames.refsUsers(Account.id(1))),
        RefNames.refsUsers(admin.id()),
        ResourceConflictException.class,
        "Not allowed to create user branch.");
  }

  @Test
  public void createGroupBranch_Conflict() throws Exception {
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(allow(Permission.CREATE).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .add(allow(Permission.PUSH).ref(RefNames.REFS_GROUPS + "*").group(REGISTERED_USERS))
        .update();
    assertCreateFails(
        BranchNameKey.create(allUsers, RefNames.refsGroups(AccountGroup.uuid("foo"))),
        RefNames.refsGroups(adminGroupUuid()),
        ResourceConflictException.class,
        "Not allowed to create group branch.");
  }

  private void blockCreateReference() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.CREATE).ref("refs/*").group(ANONYMOUS_USERS))
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

  private void assertCreateSucceeds(BranchNameKey branch) throws Exception {
    BranchInfo created = branch(branch).create(new BranchInput()).get();
    assertThat(created.ref).isEqualTo(branch.branch());
  }

  private void assertCreateFails(
      BranchNameKey branch, Class<? extends RestApiException> errType, String errMsg)
      throws Exception {
    assertCreateFails(branch, null, errType, errMsg);
  }

  private void assertCreateFails(
      BranchNameKey branch,
      String revision,
      Class<? extends RestApiException> errType,
      String errMsg)
      throws Exception {
    BranchInput in = new BranchInput();
    in.revision = revision;
    RestApiException thrown = assertThrows(errType, () -> branch(branch).create(in));
    if (errMsg != null) {
      assertThat(thrown).hasMessageThat().contains(errMsg);
    }
  }

  private void assertCreateFails(BranchNameKey branch, Class<? extends RestApiException> errType)
      throws Exception {
    assertCreateFails(branch, errType, null);
  }
}
