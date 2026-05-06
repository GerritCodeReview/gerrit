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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRequirementTemplateResource;
import java.io.IOException;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Test;

public class SubmitRequirementTemplatesCollectionTest {
  private static final Project.NameKey PARENT = Project.nameKey("All-Projects");
  private static final Project.NameKey CHILD = Project.nameKey("child");

  private CurrentUser currentUser;
  private PermissionBackend permissionBackend;
  private PermissionBackend.ForProject parentPermissions;
  private GitRepositoryManager repoManager;
  private ProjectConfig.Factory projectConfigFactory;
  private ProjectConfig parentConfig;
  private ProjectConfig childConfig;
  private ProjectResource projectResource;
  private SubmitRequirementTemplatesCollection collection;

  @Before
  public void setUp() throws IOException {
    currentUser = mock(CurrentUser.class);
    when(currentUser.isIdentifiedUser()).thenReturn(true);

    permissionBackend = mock(PermissionBackend.class);
    PermissionBackend.WithUser withUser = mock(PermissionBackend.WithUser.class);
    parentPermissions = mock(PermissionBackend.ForProject.class);
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

    repoManager = mock(GitRepositoryManager.class);
    Repository parentRepo = mock(Repository.class);
    Repository childRepo = mock(Repository.class);
    when(repoManager.openRepository(PARENT)).thenReturn(parentRepo);
    when(repoManager.openRepository(CHILD)).thenReturn(childRepo);

    projectConfigFactory = mock(ProjectConfig.Factory.class);
    parentConfig = mock(ProjectConfig.class);
    childConfig = mock(ProjectConfig.class);
    when(projectConfigFactory.create(PARENT)).thenReturn(parentConfig);
    when(projectConfigFactory.create(CHILD)).thenReturn(childConfig);

    collection =
        new SubmitRequirementTemplatesCollection(
            new SubmitRequirementTemplateLoader(
                () -> currentUser, permissionBackend, repoManager, projectConfigFactory),
            DynamicMap.emptyMap(),
            () -> mock(ListSubmitRequirementTemplates.class));
  }

  @Test
  public void parseReturnsEffectiveChildTemplateCaseInsensitive() throws Exception {
    SubmitRequirement parentTemplate =
        SubmitRequirement.builder()
            .setName("Code-Review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+1"))
            .setAllowOverrideInChildProjects(true)
            .build();
    SubmitRequirement childTemplate =
        SubmitRequirement.builder()
            .setName("code-review")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+2"))
            .setAllowOverrideInChildProjects(true)
            .build();

    when(parentConfig.getSubmitRequirementTemplateSections())
        .thenReturn(ImmutableMap.of(parentTemplate.name(), parentTemplate));
    when(childConfig.getSubmitRequirementTemplateSections())
        .thenReturn(ImmutableMap.of(childTemplate.name(), childTemplate));

    SubmitRequirementTemplateResource resource =
        collection.parse(projectResource, IdString.fromDecoded("CODE-REVIEW"));

    assertThat(resource.getSourceProject()).isEqualTo(CHILD);
    assertThat(
            resource.getSubmitRequirementTemplate().submittabilityExpression().expressionString())
        .isEqualTo("label:Code-Review=+2");
  }

  @Test
  public void parseThrowsResourceNotFoundForMissingTemplate() throws Exception {
    when(parentConfig.getSubmitRequirementTemplateSections()).thenReturn(ImmutableMap.of());
    when(childConfig.getSubmitRequirementTemplateSections()).thenReturn(ImmutableMap.of());

    assertThrows(
        ResourceNotFoundException.class,
        () -> collection.parse(projectResource, IdString.fromDecoded("missing")));
  }

  @Test
  public void parseSkipsUnreadableParentAndResolvesTemplateFromChild() throws Exception {
    SubmitRequirement childTemplate =
        SubmitRequirement.builder()
            .setName("Template")
            .setSubmittabilityExpression(SubmitRequirementExpression.create("label:Code-Review=+2"))
            .setAllowOverrideInChildProjects(true)
            .build();
    when(parentConfig.getSubmitRequirementTemplateSections()).thenReturn(ImmutableMap.of());
    when(childConfig.getSubmitRequirementTemplateSections())
        .thenReturn(ImmutableMap.of(childTemplate.name(), childTemplate));

    SubmitRequirementTemplateResource resource =
        collection.parse(projectResource, IdString.fromDecoded("template"));

    assertThat(resource.getSourceProject()).isEqualTo(CHILD);
    assertThat(resource.getSubmitRequirementTemplate().name()).isEqualTo("Template");
  }
}
