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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.api.accounts.DeleteWatchedProjectsInput;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import autovalue.shaded.com.google.common.common.collect.Lists;

public class WatchedProjectsIT extends AbstractDaemonTest {

  private static final String NEW_PROJECT_NAME = "newProjectAccess";

  @Test
  public void setAndGetWatchedProjects() throws Exception {
    String projectName1 = createProject(NEW_PROJECT_NAME).get();
    String projectName2 = createProject(NEW_PROJECT_NAME + "2").get();

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(2);

    ProjectWatchInfo wpi = new ProjectWatchInfo();
    wpi.project = projectName1;
    wpi.notifyAbandonedChanges = true;
    wpi.notifyNewChanges = true;
    wpi.notifyAllComments = true;
    projectsToWatch.add(wpi);

    wpi = new ProjectWatchInfo();
    wpi.project = projectName2;
    wpi.filter = "branch:master";
    wpi.notifySubmittedChanges = true;
    wpi.notifyNewPatchSets = true;
    projectsToWatch.add(wpi);

    List<ProjectWatchInfo> persistedWatchedProjects =
        gApi.accounts().self().setWatchedProjects(projectsToWatch);
    assertThat(persistedWatchedProjects)
        .containsExactlyElementsIn(projectsToWatch);
  }

  @Test
  public void setAndDeleteWatchedProjects() throws Exception {
    String projectName1 = createProject(NEW_PROJECT_NAME).get();
    String projectName2 = createProject(NEW_PROJECT_NAME + "2").get();

    List<ProjectWatchInfo> projectsToWatch = new LinkedList<>();

    ProjectWatchInfo wpi = new ProjectWatchInfo();
    wpi.project = projectName1;
    wpi.notifyAbandonedChanges = true;
    wpi.notifyNewChanges = true;
    wpi.notifyAllComments = true;
    projectsToWatch.add(wpi);

    wpi = new ProjectWatchInfo();
    wpi.project = projectName2;
    wpi.filter = "branch:master";
    wpi.notifySubmittedChanges = true;
    wpi.notifyNewPatchSets = true;
    projectsToWatch.add(wpi);

    // Persist watched projects
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    DeleteWatchedProjectsInput d = new DeleteWatchedProjectsInput();
    d.watchedProjects = Lists.newArrayList(projectName2);
    gApi.accounts().self().deleteWatchedProjects(d);

    List<ProjectWatchInfo> persistedWatchedProjects =
        gApi.accounts().self().getWatchedProjects();

    assertThat(persistedWatchedProjects).doesNotContain(wpi);
  }

  @Test
  public void watchNonExistingProject() throws Exception {
    String projectName = NEW_PROJECT_NAME + "3";

    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>(2);

    ProjectWatchInfo wpi = new ProjectWatchInfo();
    wpi.project = projectName;
    wpi.notifyAbandonedChanges = true;
    wpi.notifyNewChanges = true;
    wpi.notifyAllComments = true;
    projectsToWatch.add(wpi);

    exception.expect(UnprocessableEntityException.class);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);
  }

  @Test
  public void deleteNonExistingProject() throws Exception {
    String projectName = createProject(NEW_PROJECT_NAME + "4").get();

    // Let another user watch a project
    setApiUser(admin);
    List<ProjectWatchInfo> projectsToWatch = new LinkedList<>();

    ProjectWatchInfo wpi = new ProjectWatchInfo();
    wpi.project = projectName;
    wpi.notifyAbandonedChanges = true;
    wpi.notifyNewChanges = true;
    wpi.notifyAllComments = true;
    projectsToWatch.add(wpi);

    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    // Try to delete a watched project using a different user
    DeleteWatchedProjectsInput d = new DeleteWatchedProjectsInput();
    d.watchedProjects = Lists.newArrayList(projectName);
    gApi.accounts().self().deleteWatchedProjects(d);

    setApiUser(user);
    exception.expect(ResourceNotFoundException.class);
    gApi.accounts().self().deleteWatchedProjects(d);
  }
}
