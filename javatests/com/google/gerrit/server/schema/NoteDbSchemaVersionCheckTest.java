// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class NoteDbSchemaVersionCheckTest {
  private NoteDbSchemaVersionManager versionManager;
  private SitePaths sitePaths;

  @Before
  public void setup() throws Exception {
    AllProjectsName allProjectsName = new AllProjectsName("All-Projects");
    GitRepositoryManager repoManager = new InMemoryRepositoryManager();
    repoManager.createRepository(allProjectsName);
    versionManager = new NoteDbSchemaVersionManager(allProjectsName, repoManager);
    versionManager.init();

    sitePaths = new SitePaths(Paths.get("/tmp/foo"));
  }

  @Test
  public void shouldNotFailIfCurrentVersionIsExpected() {
    new NoteDbSchemaVersionCheck(versionManager, sitePaths, new Config()).start();
    // No exceptions should be thrown
  }

  @Test
  public void shouldFailIfCurrentVersionIsOneMoreThanExpected() throws IOException {
    versionManager.increment(NoteDbSchemaVersions.LATEST);

    ProvisionException e =
        assertThrows(
            ProvisionException.class,
            () -> new NoteDbSchemaVersionCheck(versionManager, sitePaths, new Config()).start());

    assertThat(e)
        .hasMessageThat()
        .contains("Unsupported schema version " + (NoteDbSchemaVersions.LATEST + 1));
  }

  @Test
  public void
      shouldNotFailWithExperimentalRollingUpgradeEnabledAndCurrentVersionIsOneMoreThanExpected()
          throws IOException {
    Config gerritConfig = new Config();
    gerritConfig.setBoolean("gerrit", null, "experimentalRollingUpgrade", true);
    versionManager.increment(NoteDbSchemaVersions.LATEST);

    NoteDbSchemaVersionCheck versionCheck =
        new NoteDbSchemaVersionCheck(versionManager, sitePaths, gerritConfig);
    versionCheck.start();
    // No exceptions should be thrown
  }
}
