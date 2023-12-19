// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;
import org.junit.Test;

@UseSsh
public class CreateProjectIT extends AbstractDaemonTest {

  @Test
  public void withValidGroupName() throws Exception {
    String newGroupName = "newGroup";
    adminRestSession.put("/groups/" + newGroupName).assertCreated();
    String newProjectName = "newProject";
    adminSshSession.exec(
        "gerrit create-project --branch master --owner " + newGroupName + " " + newProjectName);
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
  }

  @Test
  public void withInvalidGroupName() throws Exception {
    String newGroupName = "newGroup";
    adminRestSession.put("/groups/" + newGroupName).assertCreated();
    String wrongGroupName = "newG";
    String newProjectName = "newProject";
    adminSshSession.exec(
        "gerrit create-project --branch master --owner " + wrongGroupName + " " + newProjectName);
    adminSshSession.assertFailure();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isEmpty();
  }

  @Test
  public void withDotGit() throws Exception {
    String newGroupName = "newGroup";
    adminRestSession.put("/groups/" + newGroupName).assertCreated();
    String newProjectName = name("newProject");
    adminSshSession.exec(
        "gerrit create-project --branch master --owner "
            + newGroupName
            + " "
            + newProjectName
            + ".git");
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertThat(projectState.get().getName()).isEqualTo(newProjectName);
  }

  @Test
  public void withTrailingSlash() throws Exception {
    String newGroupName = "newGroup";
    adminRestSession.put("/groups/" + newGroupName).assertCreated();
    String newProjectName = name("newProject");
    adminSshSession.exec(
        "gerrit create-project --branch master --owner "
            + newGroupName
            + " "
            + newProjectName
            + "/");
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertThat(projectState.get().getName()).isEqualTo(newProjectName);
  }

  @Test
  public void withEmptyBranches() throws Exception {
    String newProjectName = name("newProject");
    adminSshSession.exec("gerrit create-project " + newProjectName);
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertHead(newProjectName, "refs/heads/master");
  }

  @Test
  public void withInitBranches() throws Exception {
    String newProjectName = name("newProject");
    adminSshSession.exec("gerrit create-project --branch init-branch " + newProjectName);
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertHead(newProjectName, "refs/heads/init-branch");
  }

  @Test
  @GerritConfig(name = "gerrit.defaultBranch", value = "refs/heads/main")
  public void withEmptyBranches_WhenDefaultBranchIsSet() throws Exception {
    String newProjectName = name("newProject");
    adminSshSession.exec("gerrit create-project " + newProjectName);
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertHead(newProjectName, "refs/heads/main");
  }
}
