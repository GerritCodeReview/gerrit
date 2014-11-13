// Copyright (C) 2014 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.TempFileUtil;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.SortedSet;

public class LocalDiskRepositoryManagerTest {

  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private Config cfg;
  private SitePaths site;
  private LocalDiskRepositoryManager repoManager;
  private RepositoryConfig repositoryConfigMock;

  @Before
  public void setUp() throws IOException {
    site = new SitePaths(TempFileUtil.createTempDirectory());
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    repositoryConfigMock = createNiceMock(RepositoryConfig.class);
    expect(repositoryConfigMock.getAllBasePaths()).andReturn(new String[] {})
        .anyTimes();
    replay(repositoryConfigMock);
    repoManager =
        new LocalDiskRepositoryManager(site, cfg,
            createNiceMock(NotesMigration.class), repositoryConfigMock);
  }

  @After
  public void tearDown() throws IOException {
    TempFileUtil.cleanup();
  }

  @Test
  public void testDefaultRepositoryLocation()
      throws RepositoryCaseMismatchException, RepositoryNotFoundException {
    Project.NameKey someProjectKey = new Project.NameKey("someProject");
    Repository repo = repoManager.createRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(
        repoManager.getBasePath(someProjectKey).getAbsolutePath());

    repo = repoManager.openRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(
        repoManager.getBasePath(someProjectKey).getAbsolutePath());

    assertThat(repoManager.getBasePath(someProjectKey).getAbsolutePath())
        .isEqualTo(repoManager.getBasePath(someProjectKey).getAbsolutePath());

    SortedSet<Project.NameKey> repoList = repoManager.list();
    assertThat(repoList.size()).isEqualTo(1);
    assertThat(repoList.toArray(new Project.NameKey[repoList.size()]))
        .isEqualTo(new Project.NameKey[] {someProjectKey});
  }

  @Test
  public void testAlternateRepositoryLocation() throws IOException {
    File alternateBasePath = TempFileUtil.createTempDirectory();
    Project.NameKey someProjectKey = new Project.NameKey("someProject");
    reset(repositoryConfigMock);
    expect(repositoryConfigMock.getBasePath(someProjectKey)).andReturn(
        alternateBasePath.getAbsolutePath()).anyTimes();
    expect(repositoryConfigMock.getAllBasePaths()).andReturn(
        new String[] {alternateBasePath.getAbsolutePath()}).anyTimes();
    replay(repositoryConfigMock);

    Repository repo = repoManager.createRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(
        alternateBasePath.getAbsolutePath());

    repo = repoManager.openRepository(someProjectKey);
    assertThat(repo.getDirectory()).isNotNull();
    assertThat(repo.getDirectory().exists()).isTrue();
    assertThat(repo.getDirectory().getParent()).isEqualTo(
        alternateBasePath.getAbsolutePath());

    assertThat(repoManager.getBasePath(someProjectKey).getAbsolutePath())
        .isEqualTo(alternateBasePath.getAbsolutePath());

    SortedSet<Project.NameKey> repoList = repoManager.list();
    assertThat(repoList.size()).isEqualTo(1);
    assertThat(repoList.toArray(new Project.NameKey[repoList.size()]))
        .isEqualTo(new Project.NameKey[] {someProjectKey});
  }

  @Test(expected = IllegalStateException.class)
  public void testRelativeAlternateLocation() {
    repositoryConfigMock = createNiceMock(RepositoryConfig.class);
    expect(repositoryConfigMock.getAllBasePaths()).andReturn(
        new String[] {"repos"}).anyTimes();
    replay(repositoryConfigMock);
    repoManager =
        new LocalDiskRepositoryManager(site, cfg,
            createNiceMock(NotesMigration.class), repositoryConfigMock);
  }
}
