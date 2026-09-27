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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.restapi.project.ListSubmitRequirementTemplates;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import org.junit.Test;

@NoHttpd
public class ListSubmitRequirementTemplatesIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Provider<ListSubmitRequirementTemplates> listSubmitRequirementTemplatesProvider;
  @Inject private ProjectsCollection projects;

  @Test
  public void noTemplates() throws Exception {
    assertThat(listTemplates(project)).isEmpty();
  }

  @Test
  public void anonymous() throws Exception {
    requestScopeOperations.setApiUserAnonymous();

    AuthException thrown =
        assertThrows(AuthException.class, () -> listSubmitRequirementTemplates(project));
    assertThat(thrown).hasMessageThat().contains("Authentication required");
  }

  @Test
  public void readsTemplatesFromCurrentProjectAndParents() throws Exception {
    putTemplate(allProjects, "Parent-Template", "label:Code-Review=+1");
    putTemplate(project, "Child-Template", "label:Code-Review=+2");

    List<SubmitRequirementInfo> response = listTemplates(project);

    assertThat(response).hasSize(2);
    assertTemplateInfo(response.get(0), "Parent-Template", allProjects);
    assertTemplateInfo(response.get(1), "Child-Template", project);
  }

  @Test
  public void returnsCurrentProjectTemplatesWhenParentIsUnreadable() throws Exception {
    putTemplate(allProjects, "Parent-Template", "label:Code-Review=+1");
    putTemplate(project, "Child-Template", "label:Code-Review=+2");
    grantReadConfigToRegisteredUsers(project);
    requestScopeOperations.setApiUser(user.id());

    List<SubmitRequirementInfo> response = listTemplates(project);

    assertThat(response).hasSize(1);
    assertTemplateInfo(response.get(0), "Child-Template", project);
  }

  @Test
  public void parentReadBlockAlsoBlocksChildConfigRead() throws Exception {
    Project.NameKey grandparent = projectOperations.newProject().name(name("grandparent")).create();
    Project.NameKey parent =
        projectOperations.newProject().name(name("parent")).parent(grandparent).create();
    Project.NameKey child =
        projectOperations.newProject().name(name("child")).parent(parent).create();

    putTemplate(grandparent, "Grandparent-Template", "label:Code-Review=+1");
    putTemplate(parent, "Parent-Template", "label:Verified=+1");
    putTemplate(child, "Child-Template", "label:Code-Review=+2");

    grantReadConfigToRegisteredUsers(grandparent);
    projectOperations
        .project(parent)
        .forUpdate()
        .add(block(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();
    projectOperations
        .project(child)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS).force(true))
        .update();

    requestScopeOperations.setApiUser(user.id());

    AuthException thrown = assertThrows(AuthException.class, () -> listTemplates(child));
    assertThat(thrown).hasMessageThat().contains("read refs/meta/config not permitted");
  }

  @Test
  public void nonOverridableParentTemplateWinsOverChildTemplate() throws Exception {
    putTemplate(allProjects, "Shared-Template", "label:Code-Review=+1", false);
    putTemplate(project, "Shared-Template", "label:Code-Review=+2", true);

    List<SubmitRequirementInfo> response = listTemplates(project);

    assertThat(response).hasSize(1);
    assertTemplateInfo(response.get(0), "Shared-Template", allProjects);
    assertThat(response.get(0).submittabilityExpression).isEqualTo("label:Code-Review=+1");
  }

  private void putTemplate(Project.NameKey projectName, String templateName, String expression) {
    putTemplate(projectName, templateName, expression, true);
  }

  private void putTemplate(
      Project.NameKey projectName,
      String templateName,
      String expression,
      boolean allowOverrideInChildProjects) {
    projectOperations
        .project(projectName)
        .forInvalidation()
        .addProjectConfigUpdater(
            cfg -> {
              cfg.setString(
                  ProjectConfig.SUBMIT_REQUIREMENT_TEMPLATE,
                  templateName,
                  ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION,
                  expression);
              cfg.setBoolean(
                  ProjectConfig.SUBMIT_REQUIREMENT_TEMPLATE,
                  templateName,
                  ProjectConfig.KEY_SR_OVERRIDE_IN_CHILD_PROJECTS,
                  allowOverrideInChildProjects);
            })
        .invalidate();
  }

  private void grantReadConfigToRegisteredUsers(Project.NameKey projectName) throws Exception {
    projectOperations
        .project(projectName)
        .forUpdate()
        .add(allow(Permission.READ).ref(RefNames.REFS_CONFIG).group(REGISTERED_USERS))
        .update();
  }

  private List<SubmitRequirementInfo> listTemplates(Project.NameKey projectName) throws Exception {
    return listSubmitRequirementTemplates(projectName).value();
  }

  private Response<List<SubmitRequirementInfo>> listSubmitRequirementTemplates(
      Project.NameKey projectName) throws Exception {
    return listSubmitRequirementTemplatesProvider.get().apply(projectResource(projectName));
  }

  private ProjectResource projectResource(Project.NameKey projectName) throws Exception {
    return projects.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(projectName.get()));
  }

  private static void assertTemplateInfo(
      SubmitRequirementInfo submitRequirementInfo, String name, Project.NameKey projectName) {
    assertThat(submitRequirementInfo.name).isEqualTo(name);
    assertThat(submitRequirementInfo.projectName).isEqualTo(projectName.get());
  }
}
