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

import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
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

    exception.expect(BadRequestException.class);
    exception.expectMessage("duplicate entry for project " + projectName);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);
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

    exception.expect(UnprocessableEntityException.class);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);
  }

  @Test
  public void deleteNonExistingProjectWatch() throws Exception {
    String projectName = project.get();

    // Let another user watch a project
    requestScopeOperations.setApiUser(admin.getId());
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
    requestScopeOperations.setApiUser(user.getId());
    gApi.accounts().self().deleteWatchedProjects(d);
  }

  @Test
  public void modifyProjectWatchUsingOmittedValues() throws Exception {
    String projectName = project.get();

    // Let another user watch a project
    requestScopeOperations.setApiUser(admin.getId());
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
    adminRestSession.post("/accounts/" + admin.username + "/watched.projects").assertOK();
  }
}
