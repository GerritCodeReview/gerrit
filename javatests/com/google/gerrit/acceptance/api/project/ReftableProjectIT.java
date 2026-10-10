// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.RefStorage;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/**
 * Exercises the reftable ref storage backend through the real project REST flows on disk. Reftable
 * requires a local {@link org.eclipse.jgit.internal.storage.file.FileRepository} and {@code
 * core.usePerRequestRefCache=false}, so these tests use {@link UseLocalDisk} and set that config.
 */
@UseLocalDisk
public class ReftableProjectIT extends AbstractDaemonTest {

  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "false")
  public void createReftableProjectWithInitialCommit() throws Exception {
    Project.NameKey name = Project.nameKey(name("reftable-with-commit"));
    ProjectInput in = new ProjectInput();
    in.name = name.get();
    in.refStorage = RefStorage.REFTABLE;
    in.createEmptyCommit = true;
    gApi.projects().create(in);

    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(Constants.R_HEADS + Constants.MASTER)).isNotNull();
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
    }
  }

  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "false")
  public void createReftableProjectWithoutInitialCommit() throws Exception {
    Project.NameKey name = Project.nameKey(name("reftable-empty"));
    ProjectInput in = new ProjectInput();
    in.name = name.get();
    in.refStorage = RefStorage.REFTABLE;
    gApi.projects().create(in);

    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
    }
  }

  @Test
  public void createReftableRefusedWhenPerRequestRefCacheEnabled() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("reftable-refused");
    in.refStorage = RefStorage.REFTABLE;
    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("usePerRequestRefCache");
  }

  private static void assertReftable(Repository repo) {
    assertThat(
            repo.getConfig()
                .getInt(
                    ConfigConstants.CONFIG_CORE_SECTION,
                    null,
                    ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION,
                    0))
        .isEqualTo(1);
    assertThat(
            repo.getConfig()
                .getString(
                    ConfigConstants.CONFIG_EXTENSIONS_SECTION,
                    null,
                    ConfigConstants.CONFIG_KEY_REF_STORAGE))
        .isEqualTo(ConfigConstants.CONFIG_REF_STORAGE_REFTABLE);
  }
}
