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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.client.RefStorage;
import com.google.gerrit.server.project.ProjectState;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
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
  @UseLocalDisk
  public void withEmptyBranches() throws Exception {
    String newProjectName = name("newProject");
    adminSshSession.exec("gerrit create-project " + newProjectName);
    adminSshSession.assertSuccess();
    Optional<ProjectState> projectState = projectCache.get(Project.nameKey(newProjectName));
    assertThat(projectState).isPresent();
    assertHead(newProjectName, "refs/heads/master");
    assertThat(gApi.projects().name(newProjectName).config().refStorage)
        .isEqualTo(RefStorage.FILES);
  }

  @Test
  @UseLocalDisk
  public void withFilesRefStorage() throws Exception {
    String newProjectName = name("files");
    adminSshSession.exec("gerrit create-project --ref-storage FILES " + newProjectName);
    adminSshSession.assertSuccess();

    assertThat(gApi.projects().name(newProjectName).config().refStorage)
        .isEqualTo(RefStorage.FILES);
  }

  @Test
  @UseLocalDisk
  public void withReftableRefStorageAndInitialCommit() throws Exception {
    String newProjectName = name("reftable");
    adminSshSession.exec(
        "gerrit create-project --ref-storage REFTABLE --empty-commit --branch main "
            + newProjectName);
    adminSshSession.assertSuccess();

    assertThat(gApi.projects().name(newProjectName).config().refStorage)
        .isEqualTo(RefStorage.REFTABLE);
    assertHead(newProjectName, "refs/heads/main");
    try (Repository repo = repoManager.openRepository(Project.nameKey(newProjectName))) {
      assertThat(repo.exactRef("refs/heads/main")).isNotNull();
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
    }
  }

  @Test
  @UseLocalDisk
  public void withReftableRefStorageWithoutInitialCommit() throws Exception {
    String newProjectName = name("reftable");
    adminSshSession.exec(
        "gerrit create-project --ref-storage REFTABLE --branch main " + newProjectName);
    adminSshSession.assertSuccess();

    assertThat(gApi.projects().name(newProjectName).config().refStorage)
        .isEqualTo(RefStorage.REFTABLE);
    try (Repository repo = repoManager.openRepository(Project.nameKey(newProjectName))) {
      assertThat(repo.exactRef("refs/heads/main")).isNull();
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
    }
  }

  @Test
  public void withInvalidRefStorage() throws Exception {
    String newProjectName = name("invalid-ref-storage");
    adminSshSession.exec("gerrit create-project --ref-storage INVALID " + newProjectName);
    adminSshSession.assertFailure();

    assertThat(projectCache.get(Project.nameKey(newProjectName))).isEmpty();
  }

  @Test
  public void helpListsRefStorage() throws Exception {
    adminSshSession.exec("gerrit create-project --help");

    assertThat(adminSshSession.getError()).contains("--ref-storage");
    assertThat(adminSshSession.getError()).contains("FILES");
    assertThat(adminSshSession.getError()).contains("REFTABLE");
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
