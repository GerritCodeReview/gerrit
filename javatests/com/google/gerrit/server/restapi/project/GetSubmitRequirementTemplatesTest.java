// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;

import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class GetSubmitRequirementTemplatesTest {
  private static final Project.NameKey PARENT = Project.nameKey("All-Projects");
  private static final Project.NameKey CHILD = Project.nameKey("child");

  private CurrentUser currentUser;
  private ProjectConfig parentConfig;
  private ProjectConfig childConfig;
  private ProjectResource projectResource;
  private GetSubmitRequirementTemplates endpoint;

  @Before
  public void setUp() throws IOException {
    currentUser = mock(CurrentUser.class);
    when(currentUser.isIdentifiedUser()).thenReturn(true);

    PermissionBackend permissionBackend = mock(PermissionBackend.class);
    PermissionBackend.WithUser withUser = mock(PermissionBackend.WithUser.class);
    PermissionBackend.ForProject parentPermissions = mock(PermissionBackend.ForProject.class);
    PermissionBackend.ForProject childPermissions = mock(PermissionBackend.ForProject.class);
    when(permissionBackend.currentUser()).thenReturn(withUser);
    when(withUser.project(PARENT)).thenReturn(parentPermissions);
    when(withUser.project(CHILD)).thenReturn(childPermissions);

    ProjectState parentState = mock(ProjectState.class);
    ProjectState childState = mock(ProjectState.class);
    when(parentState.getNameKey()).thenReturn(PARENT);
    when(childState.getNameKey()).thenReturn(CHILD);
    when(childState.treeInOrder()).thenReturn(ImmutableList.of(parentState, childState));

    projectResource = mock(ProjectResource.class);
    when(projectResource.getProjectState()).thenReturn(childState);

    GitRepositoryManager repoManager = mock(GitRepositoryManager.class);
    Repository parentRepo = mock(Repository.class);
    Repository childRepo = mock(Repository.class);
    when(repoManager.openRepository(PARENT)).thenReturn(parentRepo);
    when(repoManager.openRepository(CHILD)).thenReturn(childRepo);

    ProjectConfig.Factory projectConfigFactory = mock(ProjectConfig.Factory.class);
    parentConfig = mock(ProjectConfig.class);
    childConfig = mock(ProjectConfig.class);
    when(projectConfigFactory.create(PARENT)).thenReturn(parentConfig);
    when(projectConfigFactory.create(CHILD)).thenReturn(childConfig);

    endpoint =
        new GetSubmitRequirementTemplates(
            () -> currentUser, permissionBackend, repoManager, projectConfigFactory);
  }

  @Test
  public void readsTemplatesFromCurrentProjectAndParents() throws Exception {
    SubmitRequirement parentTemplate =
        SubmitRequirement.builder()
            .setName("Parent-Template")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setAllowOverrideInChildProjects(true)
            .build();
    SubmitRequirement childTemplate =
        SubmitRequirement.builder()
            .setName("Child-Template")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+2"))
            .setAllowOverrideInChildProjects(true)
            .build();

    when(parentConfig.getSubmitRequirementTemplateSections())
        .thenReturn(ImmutableMap.of(parentTemplate.name(), parentTemplate));
    when(childConfig.getSubmitRequirementTemplateSections())
        .thenReturn(ImmutableMap.of(childTemplate.name(), childTemplate));

    Response<List<SubmitRequirementInfo>> response = endpoint.apply(projectResource);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.value()).hasSize(2);
    assertThat(response.value().get(0).name).isEqualTo("Parent-Template");
    assertThat(response.value().get(0).projectName).isEqualTo(PARENT.get());
    assertThat(response.value().get(1).name).isEqualTo("Child-Template");
    assertThat(response.value().get(1).projectName).isEqualTo(CHILD.get());
  }

  @Test
  public void returnsEmptyListWhenNoTemplatesExist() throws Exception {
    when(parentConfig.getSubmitRequirementTemplateSections()).thenReturn(ImmutableMap.of());
    when(childConfig.getSubmitRequirementTemplateSections()).thenReturn(ImmutableMap.of());

    Response<List<SubmitRequirementInfo>> response = endpoint.apply(projectResource);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.value()).isEmpty();
  }

  @Test
  public void throwsAuthExceptionForUnauthenticatedUser() {
    when(currentUser.isIdentifiedUser()).thenReturn(false);

    assertThrows(AuthException.class, () -> endpoint.apply(projectResource));
  }

  @Test
  public void throwsAuthExceptionWhenIdentifiedUserLacksReadConfigPermission() throws Exception {
    PermissionBackend permissionBackend = mock(PermissionBackend.class);
    PermissionBackend.WithUser withUser = mock(PermissionBackend.WithUser.class);
    PermissionBackend.ForProject parentPermissions = mock(PermissionBackend.ForProject.class);
    when(permissionBackend.currentUser()).thenReturn(withUser);
    when(withUser.project(PARENT)).thenReturn(parentPermissions);
    doThrow(new AuthException("not permitted"))
        .when(parentPermissions)
        .check(ProjectPermission.READ_CONFIG);

    GetSubmitRequirementTemplates endpointWithNoPermission =
        new GetSubmitRequirementTemplates(
            () -> currentUser,
            permissionBackend,
            mock(com.google.gerrit.server.git.GitRepositoryManager.class),
            mock(ProjectConfig.Factory.class));

    AuthException thrown =
        assertThrows(AuthException.class, () -> endpointWithNoPermission.apply(projectResource));
    assertThat(thrown.getMessage()).contains(PARENT.get());
  }
}

