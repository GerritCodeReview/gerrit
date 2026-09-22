// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.RegexAllowedGroupsProvider;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class WatchedProjectsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String NEW_PROJECT_NAME = "newProjectAccess";

  @Test
  public void setAndGetWatchedProjects() throws Exception {
    String projectName1 = projectOperations.newProject().name(NEW_PROJECT_NAME).create().get();
    String projectName2 =
        projectOperations.newProject().name(NEW_PROJECT_NAME + "2").create().get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(2);

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName1;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    pwi = new ProjectWatchInfo();
    pwi.project = projectName2;
    pwi.filter = "branch:master";
    pwi.notifySubmittedChanges = true;
    pwi.notifyNewPatchSets = true;
    projectsToWatch.add(pwi);

    List<ProjectWatchInfo> persistedWatchedProjects =
        gApi.accounts().self().setWatchedProjects(projectsToWatch);
    assertThat(persistedWatchedProjects).containsAtLeastElementsIn(projectsToWatch).inOrder();
  }

  @Test
  public void setWatchedProjectsWithRegexAllowedByDefault() throws Exception {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi =
        newProjectWatchInfo(NEW_PROJECT_NAME + "3", "branch:^.*", projectsToWatch);

    assertThatWatchedProjectContainsExactly(projectsToWatch, pwi);
  }

  @Test
  @GerritConfig(
      name = RegexAllowedGroupsProvider.SECTION + "." + RegexAllowedGroupsProvider.KEY,
      value = "Registered Users")
  public void setWatchedProjectsWithRegexAllowedForTrustedUsers() throws Exception {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi =
        newProjectWatchInfo(NEW_PROJECT_NAME + "4", "branch:^.*", projectsToWatch);

    assertThatWatchedProjectContainsExactly(projectsToWatch, pwi);
  }

  @Test
  @GerritConfig(
      name = RegexAllowedGroupsProvider.SECTION + "." + RegexAllowedGroupsProvider.KEY,
      value = "Project Owners")
  public void setWatchedProjectsWithBranchFilterAllowedForUntrustedUser() throws Exception {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(1);
    ProjectWatchInfo pwi =
        newProjectWatchInfo(NEW_PROJECT_NAME + "5", "branch:master", projectsToWatch);

    assertThatWatchedProjectContainsExactly(projectsToWatch, pwi);
  }

  @Test
  @GerritConfig(
      name = RegexAllowedGroupsProvider.SECTION + "." + RegexAllowedGroupsProvider.KEY,
      value = "Project Owners")
  public void setWatchedProjectsWithRegexDeniedForUntrustedUser() throws Exception {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(1);
    newProjectWatchInfo(NEW_PROJECT_NAME + "6", "branch:^.*", projectsToWatch);

    AuthException exc =
        assertThrows(
            AuthException.class, () -> gApi.accounts().self().setWatchedProjects(projectsToWatch));
    assertThat(exc.getMessage()).contains(RegexPermissionPolicy.NOT_PERMITTED_MESSAGE);
  }

  @Test
  @GerritConfig(
      name = RegexAllowedGroupsProvider.SECTION + "." + RegexAllowedGroupsProvider.KEY,
      value = "Administrators")
  public void setWatchedProjectsWithExistingRegexAllowedForUntrustedUser() throws Exception {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(1);
    requestScopeOperations.setApiUser(admin.id());
    ProjectWatchInfo pwiWithRegex =
        newProjectWatchInfo(NEW_PROJECT_NAME + "7", "branch:^.*", projectsToWatch);
    gApi.accounts().id(user.id().get()).setWatchedProjects(projectsToWatch);

    requestScopeOperations.setApiUser(user.id());
    ProjectWatchInfo pwiWithoutRegex =
        newProjectWatchInfo(NEW_PROJECT_NAME + "8", "branch:foobar", projectsToWatch);

    assertThatWatchedProjectContainsExactly(projectsToWatch, pwiWithRegex, pwiWithoutRegex);
  }

  private ProjectWatchInfo newProjectWatchInfo(
      String projectName, String filter, List<ProjectWatchInfo> projectsToWatch) {
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectOperations.newProject().name(projectName).create().get();
    pwi.filter = filter;
    projectsToWatch.add(pwi);
    return pwi;
  }

  private void assertThatWatchedProjectContainsExactly(
      List<ProjectWatchInfo> projectsToWatch, ProjectWatchInfo... pwi) throws RestApiException {
    assertThat(gApi.accounts().self().setWatchedProjects(projectsToWatch))
        .containsExactlyElementsIn(pwi);
  }

  @Test
  public void setAndDeleteWatchedProjects() throws Exception {
    String projectName1 = projectOperations.newProject().create().get();
    String projectName2 = projectOperations.newProject().create().get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName1;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    pwi = new ProjectWatchInfo();
    pwi.project = projectName2;
    pwi.filter = "branch:master";
    pwi.notifySubmittedChanges = true;
    pwi.notifyNewPatchSets = true;
    projectsToWatch.add(pwi);

    // Persist watched projects
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    List<ProjectWatchInfo> d = Lists.newArrayList(pwi);
    gApi.accounts().self().deleteWatchedProjects(d);
    projectsToWatch.remove(pwi);

    List<ProjectWatchInfo> persistedWatchedProjects = gApi.accounts().self().getWatchedProjects();

    assertThat(persistedWatchedProjects).doesNotContain(pwi);
    assertThat(persistedWatchedProjects).containsAtLeastElementsIn(projectsToWatch);
  }

  @Test
  public void setConflictingWatches() throws Exception {
    String projectName = projectOperations.newProject().create().get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.notifySubmittedChanges = true;
    pwi.notifyNewPatchSets = true;
    projectsToWatch.add(pwi);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.accounts().self().setWatchedProjects(projectsToWatch));
    assertThat(thrown).hasMessageThat().contains("duplicate entry for project " + projectName);
  }

  @Test
  public void setAndGetEmptyWatch() throws Exception {
    String projectName = projectOperations.newProject().create().get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    projectsToWatch.add(pwi);

    gApi.accounts().self().setWatchedProjects(projectsToWatch);
    List<ProjectWatchInfo> persistedWatchedProjects = gApi.accounts().self().getWatchedProjects();
    assertThat(persistedWatchedProjects).containsAtLeastElementsIn(projectsToWatch);
  }

  @Test
  public void watchNonExistingProject() throws Exception {
    String projectName = NEW_PROJECT_NAME + "3";

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(2);

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);
    assertThrows(
        UnprocessableEntityException.class,
        () -> gApi.accounts().self().setWatchedProjects(projectsToWatch));
  }

  @Test
  public void deleteNonExistingProjectWatch() throws Exception {
    String projectName = project.get();

    // Let another user watch a project
    requestScopeOperations.setApiUser(admin.id());
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    // Try to delete a watched project using a different user
    List<ProjectWatchInfo> d = Lists.newArrayList(pwi);
    gApi.accounts().self().deleteWatchedProjects(d);

    // Check that trying to delete a non-existing watch doesn't fail
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().deleteWatchedProjects(d);
  }

  @Test
  public void modifyProjectWatchUsingOmittedValues() throws Exception {
    String projectName = project.get();

    // Let another user watch a project
    requestScopeOperations.setApiUser(admin.id());
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    // Persist a defined state
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    // Omit previously set value - will set it to false on the server
    // The response will not carry this field then as we omit sending
    // false values in JSON
    pwi.notifyNewChanges = null;

    // Perform update
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    List<ProjectWatchInfo> watchedProjects = gApi.accounts().self().getWatchedProjects();

    assertThat(watchedProjects).containsAtLeastElementsIn(projectsToWatch);
  }

  @Test
  public void setAndDeleteWatchedProjectsWithDifferentFilter() throws Exception {
    String projectName = project.get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();

    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.filter = "branch:stable";
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);

    pwi = new ProjectWatchInfo();
    pwi.project = projectName;
    pwi.filter = "branch:master";
    pwi.notifySubmittedChanges = true;
    pwi.notifyNewPatchSets = true;
    projectsToWatch.add(pwi);

    // Persist watched projects
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    List<ProjectWatchInfo> d = Lists.newArrayList(pwi);
    gApi.accounts().self().deleteWatchedProjects(d);
    projectsToWatch.remove(pwi);

    List<ProjectWatchInfo> persistedWatchedProjects = gApi.accounts().self().getWatchedProjects();

    assertThat(persistedWatchedProjects).doesNotContain(pwi);
    assertThat(persistedWatchedProjects).containsAtLeastElementsIn(projectsToWatch);
  }

  @Test
  public void postWithoutBody() throws Exception {
    adminRestSession.post("/accounts/" + admin.username() + "/watched.projects").assertOK();
  }

  @Test
  public void nullProjectThrowsBadRequestException() {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = null;
    projectsToWatch.add(pwi);
    Throwable t =
        assertThrows(
            BadRequestException.class,
            () -> gApi.accounts().self().setWatchedProjects(projectsToWatch));
    assertThat(t.getMessage()).isEqualTo("project name must be specified");
  }

  @Test
  public void emptyProjectThrowsBadRequestException() {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = "  ";
    projectsToWatch.add(pwi);
    Throwable t =
        assertThrows(
            BadRequestException.class,
            () -> gApi.accounts().self().setWatchedProjects(projectsToWatch));
    assertThat(t.getMessage()).isEqualTo("project name must be specified");
  }
}
