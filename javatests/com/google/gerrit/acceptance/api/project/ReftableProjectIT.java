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
import com.google.gerrit.extensions.api.projects.RefStorageInput;
import com.google.gerrit.extensions.client.RefStorage;
import com.google.gerrit.extensions.restapi.BadRequestException;
import java.io.File;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

/**
 * Exercises the reftable ref storage backend through the real project REST flows on disk. Reftable
 * requires a local {@link org.eclipse.jgit.internal.storage.file.FileRepository}, so these tests
 * use {@link UseLocalDisk}.
 */
@UseLocalDisk
public class ReftableProjectIT extends AbstractDaemonTest {

  @Test
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
  public void createReftableProjectWithoutInitialCommit() throws Exception {
    Project.NameKey name = Project.nameKey(name("reftable-empty"));
    ProjectInput in = new ProjectInput();
    in.name = name.get();
    in.refStorage = RefStorage.REFTABLE;
    gApi.projects().create(in);

    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
      assertThat(repo.exactRef(Constants.R_HEADS + Constants.MASTER)).isNull();
    }
  }

  @Test
  @GerritConfig(name = "core.usePerRequestRefCache", value = "true")
  public void createReftableProjectWithPerRequestRefCacheEnabled() throws Exception {
    Project.NameKey name = Project.nameKey(name("reftable-with-cache"));
    ProjectInput in = new ProjectInput();
    in.name = name.get();
    in.refStorage = RefStorage.REFTABLE;
    in.createEmptyCommit = true;
    gApi.projects().create(in);

    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(Constants.R_HEADS + Constants.MASTER)).isNotNull();
    }
  }

  @Test
  public void refStorageRejectedForInitOnlyProject() throws Exception {
    ProjectInput in = new ProjectInput();
    in.name = name("reftable-init-only");
    in.initOnly = true;
    in.refStorage = RefStorage.REFTABLE;
    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.projects().create(in));
    assertThat(thrown).hasMessageThat().contains("init-only");
  }

  @Test
  public void configReportsFilesFormat() throws Exception {
    Project.NameKey files = Project.nameKey(name("config-files"));
    gApi.projects().create(files.get());
    assertThat(gApi.projects().name(files.get()).config().refStorage).isEqualTo(RefStorage.FILES);
  }

  @Test
  public void configReportsReftableFormat() throws Exception {
    Project.NameKey reftable = Project.nameKey(name("config-reftable"));
    ProjectInput in = new ProjectInput();
    in.name = reftable.get();
    in.refStorage = RefStorage.REFTABLE;
    gApi.projects().create(in);
    assertThat(gApi.projects().name(reftable.get()).config().refStorage)
        .isEqualTo(RefStorage.REFTABLE);
  }

  @Test
  public void convertFilesProjectToReftable() throws Exception {
    Project.NameKey name = Project.nameKey(name("to-convert"));
    gApi.projects().create(name.get());

    try (Repository repo = repoManager.openRepository(name)) {
      assertThat(
              repo.getConfig()
                  .getInt(
                      ConfigConstants.CONFIG_CORE_SECTION,
                      null,
                      ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION,
                      0))
          .isEqualTo(0);
    }

    RefStorageInput in = new RefStorageInput();
    in.refStorage = RefStorage.REFTABLE;
    adminRestSession.put("/projects/" + name.get() + "/ref-storage", in).assertOK();

    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
      assertThat(new File(repo.getDirectory(), "refs.old").exists()).isTrue();
    }
  }

  @Test
  public void convertReftableProjectBackToFiles() throws Exception {
    Project.NameKey name = Project.nameKey(name("to-revert"));
    ProjectInput create = new ProjectInput();
    create.name = name.get();
    create.refStorage = RefStorage.REFTABLE;
    create.createEmptyCommit = true;
    gApi.projects().create(create);

    RefStorageInput in = new RefStorageInput();
    in.refStorage = RefStorage.FILES;
    adminRestSession.put("/projects/" + name.get() + "/ref-storage", in).assertOK();

    try (Repository repo = repoManager.openRepository(name)) {
      assertFiles(repo);
      assertThat(repo.exactRef(Constants.R_HEADS + Constants.MASTER)).isNotNull();
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
    }
    assertThat(gApi.projects().name(name.get()).config().refStorage).isEqualTo(RefStorage.FILES);
  }

  @Test
  public void convertRefusedWhenAlreadyInTargetFormat() throws Exception {
    Project.NameKey name = Project.nameKey(name("already-files"));
    gApi.projects().create(name.get());

    RefStorageInput in = new RefStorageInput();
    in.refStorage = RefStorage.FILES;
    adminRestSession.put("/projects/" + name.get() + "/ref-storage", in).assertConflict();
  }

  @Test
  public void conversionRequiresServerAdministrator() throws Exception {
    Project.NameKey name = Project.nameKey(name("conversion-permission"));
    gApi.projects().create(name.get());
    RefStorageInput input = new RefStorageInput();
    input.refStorage = RefStorage.REFTABLE;

    userRestSession.put("/projects/" + name.get() + "/ref-storage", input).assertForbidden();
    assertThat(gApi.projects().name(name.get()).config().refStorage).isEqualTo(RefStorage.FILES);
  }

  @Test
  public void conversionCanDiscardBackup() throws Exception {
    Project.NameKey name = Project.nameKey(name("conversion-no-backup"));
    gApi.projects().create(name.get());
    RefStorageInput input = new RefStorageInput();
    input.refStorage = RefStorage.REFTABLE;
    input.backup = false;

    adminRestSession.put("/projects/" + name.get() + "/ref-storage", input).assertOK();
    try (Repository repo = repoManager.openRepository(name)) {
      assertReftable(repo);
      assertThat(repo.exactRef(RefNames.REFS_CONFIG)).isNotNull();
      assertThat(new File(repo.getDirectory(), "refs.old").exists()).isFalse();
    }
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
    assertThat(new File(repo.getDirectory(), "logs/" + RefNames.REFS_CONFIG).exists()).isFalse();
  }

  private static void assertFiles(Repository repo) {
    assertThat(
            repo.getConfig()
                .getInt(
                    ConfigConstants.CONFIG_CORE_SECTION,
                    null,
                    ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION,
                    0))
        .isEqualTo(0);
    assertThat(
            repo.getConfig()
                .getString(
                    ConfigConstants.CONFIG_EXTENSIONS_SECTION,
                    null,
                    ConfigConstants.CONFIG_KEY_REF_STORAGE))
        .isNull();
  }
}
