// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.schema.NoteDbSchemaVersion;
import com.google.gerrit.server.schema.Schema_185;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class Schema_185IT extends AbstractDaemonTest {
  @Inject private NoteDbSchemaVersion.Arguments args;

  @Test
  @GerritConfig(name = "change.cacheAutomerge", value = "false")
  @UseLocalDisk
  public void upgradeCreatesAutoMerge() throws Exception {
    // We use change.cacheAutomerge = false so that pushing a new change does not persist the
    // auto-merge on disk. We use local disk so that the auto-merge computation of the upgrade is
    // persisted.
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master", "my_file.txt");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).isEmpty();
    }

    Schema_185 upgrade = new Schema_185();
    upgrade.upgrade(args, new TestUpdateUI());

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).hasSize(1);
    }
  }

  @Test
  @GerritConfig(name = "change.cacheAutomerge", value = "false")
  @UseLocalDisk
  public void upgradeIsIdempotent() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master", "my_file.txt");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).isEmpty();
    }

    Schema_185 upgrade = new Schema_185();
    upgrade.upgrade(args, new TestUpdateUI());
    upgrade.upgrade(args, new TestUpdateUI());

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).hasSize(1);
    }
  }

  @Test
  @GerritConfig(name = "change.cacheAutomerge", value = "false")
  @UseLocalDisk
  public void upgradeLarge() throws Exception {
    for (int i = 0; i < 8; i++) {
      PushOneCommit.Result r = createMergeCommitChange("refs/for/master", "my_file.txt");
    }

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).isEmpty();
    }

    Schema_185 upgrade = new Schema_185();
    upgrade.setRefUpdateChunk(3);
    upgrade.upgrade(args, new TestUpdateUI());

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.getRefDatabase().getRefsByPrefix("refs/cache-automerge")).hasSize(8);
    }
  }
}
