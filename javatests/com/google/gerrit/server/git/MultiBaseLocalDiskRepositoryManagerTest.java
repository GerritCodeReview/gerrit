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
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.config.RepositoryConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testing.GerritBaseTests;
import com.google.gerrit.testing.TempFileUtil;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MultiBaseLocalDiskRepositoryManagerTest extends GerritBaseTests {
  private Config cfg;
  private SitePaths site;
  private MultiBaseLocalDiskRepositoryManager repoManager;
  private RepositoryConfig configMock;

  @Before
  public void setUp() throws IOException {
    site = new SitePaths(TempFileUtil.createTempDirectory().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    configMock = createNiceMock(RepositoryConfig.class);
    expect(configMock.getAllBasePaths()).andReturn(ImmutableList.of()).anyTimes();
    replay(configMock);
    repoManager = new MultiBaseLocalDiskRepositoryManager(site, cfg, configMock);
  }

  @After
  public void tearDown() throws IOException {
    TempFileUtil.cleanup();
  }

  @Test
  public void defaultRepositoryLocation()
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException {
    Project.NameKey someProjectKey = new Project.NameKey("someProject");
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
    Path alternateBasePath = TempFileUtil.createTempDirectory().toPath();
    Project.NameKey someProjectKey = new Project.NameKey("someProject");
    reset(configMock);
    expect(configMock.getBasePath(someProjectKey)).andReturn(alternateBasePath).anyTimes();
    expect(configMock.getAllBasePaths()).andReturn(ImmutableList.of(alternateBasePath)).anyTimes();
    replay(configMock);

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
    Project.NameKey basePathProject = new Project.NameKey("basePathProject");
    Project.NameKey altPathProject = new Project.NameKey("altPathProject");
    Project.NameKey misplacedProject1 = new Project.NameKey("misplacedProject1");
    Project.NameKey misplacedProject2 = new Project.NameKey("misplacedProject2");

    Path alternateBasePath = TempFileUtil.createTempDirectory().toPath();

    reset(configMock);
    expect(configMock.getBasePath(altPathProject)).andReturn(alternateBasePath).anyTimes();
    expect(configMock.getBasePath(misplacedProject2)).andReturn(alternateBasePath).anyTimes();
    expect(configMock.getAllBasePaths()).andReturn(ImmutableList.of(alternateBasePath)).anyTimes();
    replay(configMock);

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

  @Test(expected = IllegalStateException.class)
  public void testRelativeAlternateLocation() {
    configMock = createNiceMock(RepositoryConfig.class);
    expect(configMock.getAllBasePaths()).andReturn(ImmutableList.of(Paths.get("repos"))).anyTimes();
    replay(configMock);
    repoManager = new MultiBaseLocalDiskRepositoryManager(site, cfg, configMock);
  }
}
