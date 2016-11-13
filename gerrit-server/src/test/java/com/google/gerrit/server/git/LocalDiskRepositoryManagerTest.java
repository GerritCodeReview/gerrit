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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import java.io.IOException;
import java.nio.file.Path;
import org.easymock.EasyMockSupport;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Test;

public class LocalDiskRepositoryManagerTest extends EasyMockSupport {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private Config cfg;
  private SitePaths site;
  private LocalDiskRepositoryManager repoManager;

  @Before
  public void setUp() throws Exception {
    site = new SitePaths(TempFileUtil.createTempDirectory().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    repoManager = new LocalDiskRepositoryManager(site, cfg);
    repoManager.start();
  }

  @Test(expected = IllegalStateException.class)
  public void testThatNullBasePathThrowsAnException() {
    new LocalDiskRepositoryManager(site, new Config());
  }

  @Test
  public void testProjectCreation() throws Exception {
    Project.NameKey projectA = new Project.NameKey("projectA");
    try (Repository repo = repoManager.createRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    try (Repository repo = repoManager.openRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    assertThat(repoManager.list()).containsExactly(projectA);
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithEmptyName() throws Exception {
    repoManager.createRepository(new Project.NameKey(""));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithTrailingSlash() throws Exception {
    repoManager.createRepository(new Project.NameKey("projectA/"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithBackSlash() throws Exception {
    repoManager.createRepository(new Project.NameKey("a\\projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationAbsolutePath() throws Exception {
    repoManager.createRepository(new Project.NameKey("/projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationStartingWithDotDot() throws Exception {
    repoManager.createRepository(new Project.NameKey("../projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationContainsDotDot() throws Exception {
    repoManager.createRepository(new Project.NameKey("a/../projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationDotPathSegment() throws Exception {
    repoManager.createRepository(new Project.NameKey("a/./projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithTwoSlashes() throws Exception {
    repoManager.createRepository(new Project.NameKey("a//projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithPathSegmentEndingByDotGit() throws Exception {
    repoManager.createRepository(new Project.NameKey("a/b.git/projectA"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithQuestionMark() throws Exception {
    repoManager.createRepository(new Project.NameKey("project?A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithPercentageSign() throws Exception {
    repoManager.createRepository(new Project.NameKey("project%A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithWidlcard() throws Exception {
    repoManager.createRepository(new Project.NameKey("project*A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithColon() throws Exception {
    repoManager.createRepository(new Project.NameKey("project:A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithLessThatSign() throws Exception {
    repoManager.createRepository(new Project.NameKey("project<A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithGreaterThatSign() throws Exception {
    repoManager.createRepository(new Project.NameKey("project>A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithPipe() throws Exception {
    repoManager.createRepository(new Project.NameKey("project|A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithDollarSign() throws Exception {
    repoManager.createRepository(new Project.NameKey("project$A"));
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testProjectCreationWithCarriageReturn() throws Exception {
    repoManager.createRepository(new Project.NameKey("project\\rA"));
  }

  @Test
  public void testOpenRepositoryCreatedDirectlyOnDisk() throws Exception {
    Project.NameKey projectA = new Project.NameKey("projectA");
    createRepository(repoManager.getBasePath(projectA), projectA.get());
    try (Repository repo = repoManager.openRepository(projectA)) {
      assertThat(repo).isNotNull();
    }
    assertThat(repoManager.list()).containsExactly(projectA);
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testOpenRepositoryInvalidName() throws Exception {
    repoManager.openRepository(new Project.NameKey("project%?|<>A"));
  }

  @Test
  public void testList() throws Exception {
    Project.NameKey projectA = new Project.NameKey("projectA");
    createRepository(repoManager.getBasePath(projectA), projectA.get());

    Project.NameKey projectB = new Project.NameKey("path/projectB");
    createRepository(repoManager.getBasePath(projectB), projectB.get());

    Project.NameKey projectC = new Project.NameKey("anotherPath/path/projectC");
    createRepository(repoManager.getBasePath(projectC), projectC.get());
    // create an invalid git repo named only .git
    repoManager.getBasePath(null).resolve(".git").toFile().mkdir();
    // create an invalid repo name
    createRepository(repoManager.getBasePath(null), "project?A");
    assertThat(repoManager.list()).containsExactly(projectA, projectB, projectC);
  }

  @Test
  public void testGetSetProjectDescription() throws Exception {
    Project.NameKey projectA = new Project.NameKey("projectA");
    try (Repository repo = repoManager.createRepository(projectA)) {
      assertThat(repo).isNotNull();
    }

    assertThat(repoManager.getProjectDescription(projectA)).isNull();
    repoManager.setProjectDescription(projectA, "projectA description");
    assertThat(repoManager.getProjectDescription(projectA)).isEqualTo("projectA description");

    repoManager.setProjectDescription(projectA, "");
    assertThat(repoManager.getProjectDescription(projectA)).isNull();
  }

  @Test(expected = RepositoryNotFoundException.class)
  public void testGetProjectDescriptionFromUnexistingRepository() throws Exception {
    repoManager.getProjectDescription(new Project.NameKey("projectA"));
  }

  private void createRepository(Path directory, String projectName) throws IOException {
    String n = projectName + Constants.DOT_GIT_EXT;
    FileKey loc = FileKey.exact(directory.resolve(n).toFile(), FS.DETECTED);
    try (Repository db = RepositoryCache.open(loc, false)) {
      db.create(true /* bare */);
    }
  }
}
