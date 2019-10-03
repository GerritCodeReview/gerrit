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
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.SortedSet;
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

public class MultiBaseLocalDiskRepositoryManagerTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Config cfg;
  private SitePaths site;
  private MultiBaseLocalDiskRepositoryManager repoManager;
  private RepositoryConfig configMock;

  @Before
  public void setUp() throws IOException {
    site = new SitePaths(temporaryFolder.newFolder().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    configMock = mock(RepositoryConfig.class);
    when(configMock.getAllBasePaths()).thenReturn(ImmutableList.of());
    repoManager = new MultiBaseLocalDiskRepositoryManager(site, cfg, configMock);
  }

  @Test
  public void defaultRepositoryLocation()
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    Project.NameKey someProjectKey = Project.nameKey("someProject");
    Repository repo = repoManager.createRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent())
        .isEqualTo(repoManager.getBasePath(someProjectKey).toAbsolutePath().toString());

    repo = repoManager.openRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent())
        .isEqualTo(repoManager.getBasePath(someProjectKey).toAbsolutePath().toString());

    assertThat(repoManager.getBasePath(someProjectKey).toAbsolutePath().toString())
        .isEqualTo(repoManager.getBasePath(someProjectKey).toAbsolutePath().toString());

    SortedSet<Project.NameKey> repoList = repoManager.list();
    assertThat(repoList).hasSize(1);
    assertThat(repoList.toArray(new Project.NameKey[repoList.size()]))
        .isEqualTo(new Project.NameKey[] {someProjectKey});
  }

  @Test
  public void alternateRepositoryLocation() throws IOException {
    Path alternateBasePath = temporaryFolder.newFolder().toPath();
    Project.NameKey someProjectKey = Project.nameKey("someProject");
    when(configMock.getBasePath(someProjectKey)).thenReturn(alternateBasePath);
    when(configMock.getAllBasePaths()).thenReturn(ImmutableList.of(alternateBasePath));

    Repository repo = repoManager.createRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(alternateBasePath.toString());

    repo = repoManager.openRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(alternateBasePath.toString());

    assertThat(repoManager.getBasePath(someProjectKey).toAbsolutePath().toString())
        .isEqualTo(alternateBasePath.toString());

    SortedSet<Project.NameKey> repoList = repoManager.list();
    assertThat(repoList).hasSize(1);
    assertThat(repoList.toArray(new Project.NameKey[repoList.size()]))
        .isEqualTo(new Project.NameKey[] {someProjectKey});
  }

  @Test
  public void listReturnRepoFromProperLocation() throws IOException {
    Project.NameKey basePathProject = Project.nameKey("basePathProject");
    Project.NameKey altPathProject = Project.nameKey("altPathProject");
    Project.NameKey misplacedProject1 = Project.nameKey("misplacedProject1");
    Project.NameKey misplacedProject2 = Project.nameKey("misplacedProject2");

    Path alternateBasePath = temporaryFolder.newFolder().toPath();

    when(configMock.getBasePath(altPathProject)).thenReturn(alternateBasePath);
    when(configMock.getBasePath(misplacedProject2)).thenReturn(alternateBasePath);
    when(configMock.getAllBasePaths()).thenReturn(ImmutableList.of(alternateBasePath));

    repoManager.createRepository(basePathProject);
    repoManager.createRepository(altPathProject);
    // create the misplaced ones without the repomanager otherwise they would
    // end up at the proper place.
    createRepository(repoManager.getBasePath(basePathProject), misplacedProject2);
    createRepository(alternateBasePath, misplacedProject1);

    SortedSet<Project.NameKey> repoList = repoManager.list();
    assertThat(repoList).hasSize(2);
    assertThat(repoList.toArray(new Project.NameKey[repoList.size()]))
        .isEqualTo(new Project.NameKey[] {altPathProject, basePathProject});
  }

  private void createRepository(Path directory, Project.NameKey projectName) throws IOException {
    String n = projectName.get() + Constants.DOT_GIT_EXT;
    FileKey loc = FileKey.exact(directory.resolve(n).toFile(), FS.DETECTED);
    try (Repository db = RepositoryCache.open(loc, false)) {
      db.create(true /* bare */);
    }
  }

  @Test
  public void testRelativeAlternateLocation() {
    assertThrows(
        IllegalStateException.class,
        () -> {
          configMock = mock(RepositoryConfig.class);
          when(configMock.getAllBasePaths()).thenReturn(ImmutableList.of(Paths.get("repos")));
          repoManager = new MultiBaseLocalDiskRepositoryManager(site, cfg, configMock);
        });
  }
}
