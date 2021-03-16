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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.ioutil.HostPlatform;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LocalDiskRepositoryManagerTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Config cfg;
  private SitePaths site;
  private LocalDiskRepositoryManager repoManager;

  @Before
  public void setUp() throws Exception {
    site = new SitePaths(temporaryFolder.newFolder().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    repoManager = new LocalDiskRepositoryManager(site, cfg);
  }

  @Test
  public void testThatNullBasePathThrowsAnException() {
    assertThrows(
        IllegalStateException.class, () -> new LocalDiskRepositoryManager(site, new Config()));
  }

  @Test
  public void projectCreation() throws Exception {
    Project.NameKey projectA = Project.nameKey("projectA");
    try (Repository repo = repoManager.createRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    try (Repository repo = repoManager.openRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    assertThat(repoManager.list()).containsExactly(projectA);
  }

  @Test
  public void testProjectCreationWithEmptyName() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class, () -> repoManager.createRepository(Project.nameKey("")));
  }

  @Test
  public void testProjectCreationWithTrailingSlash() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("projectA/")));
  }

  @Test
  public void testProjectCreationWithBackSlash() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("a\\projectA")));
  }

  @Test
  public void testProjectCreationAbsolutePath() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("/projectA")));
  }

  @Test
  public void testProjectCreationStartingWithDotDot() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("../projectA")));
  }

  @Test
  public void testProjectCreationContainsDotDot() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("a/../projectA")));
  }

  @Test
  public void testProjectCreationDotPathSegment() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("a/./projectA")));
  }

  @Test
  public void testProjectCreationWithTwoSlashes() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("a//projectA")));
  }

  @Test
  public void testProjectCreationWithPathSegmentEndingByDotGit() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("a/b.git/projectA")));
  }

  @Test
  public void testProjectCreationWithQuestionMark() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project?A")));
  }

  @Test
  public void testProjectCreationWithPercentageSign() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project%A")));
  }

  @Test
  public void testProjectCreationWithWidlcard() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project*A")));
  }

  @Test
  public void testProjectCreationWithColon() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project:A")));
  }

  @Test
  public void testProjectCreationWithLessThatSign() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project<A")));
  }

  @Test
  public void testProjectCreationWithGreaterThatSign() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project>A")));
  }

  @Test
  public void testProjectCreationWithPipe() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project|A")));
  }

  @Test
  public void testProjectCreationWithDollarSign() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project$A")));
  }

  @Test
  public void testProjectCreationWithCarriageReturn() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.createRepository(Project.nameKey("project\\rA")));
  }

  @Test
  public void testProjectRecreation() throws Exception {
    repoManager.createRepository(Project.nameKey("a"));
    assertThrows(
        IllegalStateException.class, () -> repoManager.createRepository(Project.nameKey("a")));
  }

  @Test
  public void testProjectRecreationAfterRestart() throws Exception {
    repoManager.createRepository(Project.nameKey("a"));
    LocalDiskRepositoryManager newRepoManager = new LocalDiskRepositoryManager(site, cfg);
    assertThrows(
        IllegalStateException.class, () -> newRepoManager.createRepository(Project.nameKey("a")));
  }

  @Test
  public void openRepositoryCreatedDirectlyOnDisk() throws Exception {
    Project.NameKey projectA = Project.nameKey("projectA");
    createRepository(repoManager.getBasePath(projectA), projectA.get());
    try (Repository repo = repoManager.openRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    assertThat(repoManager.list()).containsExactly(projectA);
  }

  @Test
  public void testNameCaseMismatch() throws Exception {
    assume().that(HostPlatform.isWin32() || HostPlatform.isMac()).isTrue();
    repoManager.createRepository(Project.nameKey("a"));
    assertThrows(
        RepositoryCaseMismatchException.class,
        () -> repoManager.createRepository(Project.nameKey("A")));
  }

  @Test
  public void testNameCaseMismatchWithSymlink() throws Exception {
    assume().that(HostPlatform.isWin32() || HostPlatform.isMac()).isTrue();
    Project.NameKey name = Project.nameKey("a");
    repoManager.createRepository(name);
    createSymLink(name, "b.git");
    assertThrows(
        RepositoryCaseMismatchException.class,
        () -> repoManager.createRepository(Project.nameKey("B")));
  }

  @Test
  public void testNameCaseMismatchAfterRestart() throws Exception {
    assume().that(HostPlatform.isWin32() || HostPlatform.isMac()).isTrue();
    Project.NameKey name = Project.nameKey("a");
    repoManager.createRepository(name);

    LocalDiskRepositoryManager newRepoManager = new LocalDiskRepositoryManager(site, cfg);
    assertThrows(
        RepositoryCaseMismatchException.class,
        () -> newRepoManager.createRepository(Project.nameKey("A")));
  }

  @Test
  public void testRepositoryCanPerformGC() throws Exception {
    assertThat(repoManager.canPerformGC()).isTrue();
  }

  private void createSymLink(Project.NameKey project, String link) throws IOException {
    Path base = repoManager.getBasePath(project);
    Path projectDir = base.resolve(project.get() + ".git");
    Path symlink = base.resolve(link);
    Files.createSymbolicLink(symlink, projectDir);
  }

  @Test
  public void testOpenRepositoryInvalidName() throws Exception {
    assertThrows(
        RepositoryNotFoundException.class,
        () -> repoManager.openRepository(Project.nameKey("project%?|<>A")));
  }

  @Test
  public void list() throws Exception {
    Project.NameKey projectA = Project.nameKey("projectA");
    createRepository(repoManager.getBasePath(projectA), projectA.get());

    Project.NameKey projectB = Project.nameKey("path/projectB");
    createRepository(repoManager.getBasePath(projectB), projectB.get());

    Project.NameKey projectC = Project.nameKey("anotherPath/path/projectC");
    createRepository(repoManager.getBasePath(projectC), projectC.get());
    // create an invalid git repo named only .git
    repoManager.getBasePath(null).resolve(".git").toFile().mkdir();
    // create an invalid repo name
    createRepository(repoManager.getBasePath(null), "project?A");
    assertThat(repoManager.list()).containsExactly(projectA, projectB, projectC);
  }

  private void createRepository(Path directory, String projectName) throws IOException {
    String n = projectName + Constants.DOT_GIT_EXT;
    FileKey loc = FileKey.exact(directory.resolve(n).toFile(), FS.DETECTED);
    try (Repository db = RepositoryCache.open(loc, false)) {
      db.create(true /* bare */);
    }
  }
}
