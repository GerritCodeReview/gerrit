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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DelegateRepositoryTest {
  private static final Project.NameKey FOO = Project.nameKey("foo");

  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private LocalDiskRepositoryManager localDiskRepositoryManager;

  @Before
  public void setup() throws Exception {
    SitePaths site = new SitePaths(temporaryFolder.newFolder().toPath());
    site.resolve("git").toFile().mkdir();
    Config cfg = new Config();
    cfg.setString("gerrit", null, "basePath", "git");
    localDiskRepositoryManager = new LocalDiskRepositoryManager(site, cfg);
  }

  @Test
  public void shouldDelegateRepository() throws IOException {
    Repository repository = localDiskRepositoryManager.createRepository(FOO);
    assertThat(new DelegateRepository(repository).delegate()).isSameInstanceAs(repository);
  }
}
