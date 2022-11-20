// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.httpd.gitweb;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.GitwebCgiConfig;
import com.google.gerrit.server.config.GitwebConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectCache;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GitwebServletTest {
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private Config cfg;
  private SitePaths site;
  private LocalDiskRepositoryManager repoManager;
  private ProjectCache projectCache;
  private PermissionBackend permissionBackendMock;
  private GitwebCgiConfig gitWebCgiConfig;
  private GitwebConfig gitWebConfig;
  private GitwebServlet servlet;
  private AllProjectsName allProjectsName;

  @Before
  public void setUp() throws Exception {
    site = new SitePaths(temporaryFolder.newFolder().toPath());
    site.resolve("git").toFile().mkdir();
    cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    repoManager = new LocalDiskRepositoryManager(site, cfg);
    projectCache = mock(ProjectCache.class);
    permissionBackendMock = mock(PermissionBackend.class);
    gitWebCgiConfig = new GitwebCgiConfig(site, cfg);
    gitWebConfig = new GitwebConfig(gitWebCgiConfig, cfg, null);
    allProjectsName = new AllProjectsName(AllProjectsNameProvider.DEFAULT);
    // All-Projects must exist prior to calling GitwebServlet ctor
    repoManager.createRepository(allProjectsName);
    servlet =
        new GitwebServlet(
            repoManager,
            projectCache,
            permissionBackendMock,
            null,
            site,
            cfg,
            null,
            null,
            gitWebConfig,
            gitWebCgiConfig,
            allProjectsName);
  }

  @Test
  public void projectRootSetToBasePath() throws Exception {
    Project.NameKey foo = Project.nameKey("foo");
    try (Repository repo = repoManager.createRepository(foo)) {
      assertThat(repoManager.getBasePath(foo).toAbsolutePath().toString())
          .isEqualTo(repo.getDirectory().getParent());
      assertThat(servlet.getProjectRoot(foo)).isEqualTo(repo.getDirectory().getParent());
    }
  }
}
