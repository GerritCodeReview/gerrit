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
import static com.google.gerrit.acceptance.rest.project.ProjectAssert.assertThatNameList;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;

@NoHttpd
public class ListChildProjectsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void listChildrenOfNonExistingProject_NotFound() throws Exception {
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class,
            () -> gApi.projects().name(name("non-existing")).child("children"));
    assertThat(thrown).hasMessageThat().contains("non-existing");
  }

  @Test
  public void listNoChildren() throws Exception {
    assertThatNameList(gApi.projects().name(project.get()).children()).isEmpty();
  }

  @Test
  public void listChildren() throws Exception {
    Project.NameKey child1 = projectOperations.newProject().create();
    Project.NameKey child1_1 = projectOperations.newProject().parent(child1).create();
    Project.NameKey child1_2 = projectOperations.newProject().parent(child1).create();

    assertThatNameList(gApi.projects().name(child1.get()).children()).isInOrder();
    assertThatNameList(gApi.projects().name(child1.get()).children())
        .containsExactly(child1_1, child1_2);
  }

  @Test
  public void listChildrenWithLimit() throws Exception {
    String prefix = RandomStringUtils.randomAlphabetic(8);
    Project.NameKey child1 = projectOperations.newProject().name(prefix + "p1").create();
    Project.NameKey child1_1 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.1").create();
    projectOperations.newProject().parent(child1).name(prefix + "p1.2").create();

    assertThatNameList(gApi.projects().name(child1.get()).children(1)).containsExactly(child1_1);
  }

  @Test
  public void listChildrenRecursively() throws Exception {
    String prefix = RandomStringUtils.randomAlphabetic(8);
    Project.NameKey child1 = projectOperations.newProject().name(prefix + "p1").create();
    Project.NameKey child1_1 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.1").create();
    Project.NameKey child1_2 =
        projectOperations.newProject().parent(child1).name(prefix + "p1.2").create();
    Project.NameKey child1_1_1 =
        projectOperations.newProject().parent(child1_1).name(prefix + "p1.1.1").create();
    Project.NameKey child1_1_1_1 =
        projectOperations.newProject().parent(child1_1_1).name(prefix + "p1.1.1.1").create();

    assertThatNameList(gApi.projects().name(child1.get()).children(true))
        .containsExactly(child1_1, child1_1_1, child1_1_1_1, child1_2)
        .inOrder();
  }

  @Test
  public void listChildrenVisibility() throws Exception {
    Project.NameKey parent = projectOperations.newProject().createEmptyCommit(true).create();
    Project.NameKey project =
        projectOperations.newProject().createEmptyCommit(true).parent(parent).create();

    AccountGroup.UUID privilegedGroupUuid =
        groupOperations.newGroup().name(name("privilegedGroup")).create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.READ).ref("refs/*").group(privilegedGroupUuid))
        .add(block(Permission.READ).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();

    TestAccount privilegedUser =
        accountCreator.create("privilegedUser", "snowden@nsa.gov", "Ed Snowden", null);
    groupOperations.group(privilegedGroupUuid).forUpdate().addMember(privilegedUser.id()).update();

    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.projects().name(parent.get()).children(false)).isEmpty();
    requestScopeOperations.setApiUser(privilegedUser.id());
    assertThat(gApi.projects().name(parent.get()).children(false)).isNotEmpty();
  }
}
