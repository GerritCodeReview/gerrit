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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.inject.Inject;
import org.junit.Test;

@NoHttpd
public class SetParentIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void setParentNotAllowed() throws Exception {
    String parent = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).parent(parent));
  }

  @Test
  @GerritConfig(name = "receive.allowProjectOwnersToChangeParent", value = "true")
  public void setParentNotAllowedForNonOwners() throws Exception {
    String parent = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    assertThrows(AuthException.class, () -> gApi.projects().name(project.get()).parent(parent));
  }

  @Test
  @GerritConfig(name = "receive.allowProjectOwnersToChangeParent", value = "true")
  public void setParentAllowedByAdminWhenAllowProjectOwnersEnabled() throws Exception {
    String parent = projectOperations.newProject().create().get();

    gApi.projects().name(project.get()).parent(parent);
    assertThat(gApi.projects().name(project.get()).parent()).isEqualTo(parent);

    // When the parent name is not explicitly set, it should be
    // set to "All-Projects".
    gApi.projects().name(project.get()).parent(null);
    assertThat(gApi.projects().name(project.get()).parent())
        .isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  @GerritConfig(name = "receive.allowProjectOwnersToChangeParent", value = "true")
  public void setParentAllowedForOwners() throws Exception {
    String parent = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.OWNER).ref("refs/*").group(SystemGroupBackend.REGISTERED_USERS))
        .update();
    gApi.projects().name(project.get()).parent(parent);
    assertThat(gApi.projects().name(project.get()).parent()).isEqualTo(parent);
  }

  @Test
  public void setParent() throws Exception {
    String parent = projectOperations.newProject().create().get();

    gApi.projects().name(project.get()).parent(parent);
    assertThat(gApi.projects().name(project.get()).parent()).isEqualTo(parent);

    // When the parent name is not explicitly set, it should be
    // set to "All-Projects".
    gApi.projects().name(project.get()).parent(null);
    assertThat(gApi.projects().name(project.get()).parent())
        .isEqualTo(AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void setParentForAllProjectsNotAllowed() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(allProjects.get()).parent(project.get()));
    assertThat(thrown)
        .hasMessageThat()
        .contains("cannot set parent of " + AllProjectsNameProvider.DEFAULT);
  }

  @Test
  public void setParentToSelfNotAllowed() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).parent(project.get()));
    assertThat(thrown).hasMessageThat().contains("cannot set parent to self");
  }

  @Test
  public void setParentToOwnChildNotAllowed() throws Exception {
    String child = projectOperations.newProject().parent(project).create().get();
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).parent(child));
    assertThat(thrown).hasMessageThat().contains("cycle exists between");
  }

  @Test
  public void setParentToGrandchildNotAllowed() throws Exception {
    Project.NameKey child = projectOperations.newProject().parent(project).create();
    String grandchild = projectOperations.newProject().parent(child).create().get();
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).parent(grandchild));
    assertThat(thrown).hasMessageThat().contains("cycle exists between");
  }

  @Test
  public void setParentToNonexistentProject() throws Exception {
    UnprocessableEntityException thrown =
        assertThrows(
            UnprocessableEntityException.class,
            () -> gApi.projects().name(project.get()).parent("non-existing"));
    assertThat(thrown).hasMessageThat().contains("not found");
  }

  @Test
  public void setParentToAllUsersNotAllowed() throws Exception {
    ResourceConflictException thrown =
        assertThrows(
            ResourceConflictException.class,
            () -> gApi.projects().name(project.get()).parent(allUsers.get()));
    assertThat(thrown)
        .hasMessageThat()
        .contains(String.format("Cannot inherit from '%s' project", allUsers.get()));
  }

  @Test
  public void setParentForAllUsersMustBeAllProjects() throws Exception {
    gApi.projects().name(allUsers.get()).parent(allProjects.get());

    String parent = projectOperations.newProject().create().get();

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> gApi.projects().name(allUsers.get()).parent(parent));
    assertThat(thrown).hasMessageThat().contains("All-Users must inherit from All-Projects");
  }
}
