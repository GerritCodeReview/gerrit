// Copyright (C) 2020 The Android Open Source Project
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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.change.ChangeOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.group.InternalGroup;
import com.google.inject.Inject;
import org.junit.Before;
import org.junit.Test;

public class GetBranchIT extends AbstractDaemonTest {
  @Inject private ChangeOperations changeOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setUp() throws Exception {
    // add block permissions that make the tests pass
    projectOperations
        .project(allProjects)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/changes/*").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/groups/*").group(ANONYMOUS_USERS))
        .update();
    projectOperations
        .project(allUsers)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/meta/external-ids").group(ANONYMOUS_USERS))
        .add(block(Permission.READ).ref("refs/users/*").group(ANONYMOUS_USERS))
        .update();
  }

  @Test
  public void cannotGetNonExistingBranch() throws Exception {
    assertBranchNotFound(project, RefNames.fullName("non-existing"));
  }

  @Test
  public void cannotGetChangeRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    Change.Id changeId = changeOperations.newChange().create();
    assertBranchNotFound(project, RefNames.patchSetRef(PatchSet.id(changeId, 1)));
  }

  @Test
  public void cannotGetChangeRefOfNonVisibleChange() throws Exception {
    String branchName = "master";
    Change.Id changeId = changeOperations.newChange().branch(branchName).create();

    // block read access to the branch
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.fullName(branchName)).group(ANONYMOUS_USERS))
        .update();

    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.patchSetRef(PatchSet.id(changeId, 1)));
  }

  @Test
  public void cannotGetChangeMetaRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    Change.Id changeId = changeOperations.newChange().create();
    assertBranchNotFound(project, RefNames.changeMetaRef(changeId));
  }

  @Test
  public void cannotGetRefsMetaConfig() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(project, RefNames.REFS_CONFIG);
  }

  @Test
  public void cannotGetUserRefOfOtherUser() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.refsUsers(admin.id()));
  }

  @Test
  public void cannotGetExternalIdsRefs() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    assertBranchNotFound(allUsers, RefNames.REFS_EXTERNAL_IDS);
  }

  @Test
  public void cannotGetGroupRef() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    InternalGroup adminGroup =
        groupCache
            .get(AccountGroup.nameKey("Administrators"))
            .orElseThrow(() -> new IllegalStateException("admin group not found"));
    assertBranchNotFound(allUsers, RefNames.refsGroups(adminGroup.getGroupUUID()));
  }

  private void assertBranchNotFound(Project.NameKey project, String ref) {
    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(project.get()).branch(ref).get());
    assertThat(exception).hasMessageThat().isEqualTo("Not found: " + ref);
  }
}
