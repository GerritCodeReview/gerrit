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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.notedb.ConfigNotesMigration;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.testutil.TempFileUtil;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RebuildNoteDbIT {
  private SitePaths sitePaths;
  private String sitePath;
  private StoredConfig gerritConfig;

  @Before
  public void setUp() throws Exception {
    sitePaths = new SitePaths(TempFileUtil.createTempDirectory().toPath());
    sitePath = sitePaths.site_path.toString();
    gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
  }

  @After
  public void tearDown() throws Exception {
    TempFileUtil.cleanup();
  }

  @Test
  public void rebuildEmptySiteStartingWithNoteDbEnabled() throws Exception {
    initSite();
    setNotesMigrationState(NotesMigrationState.NOTE_DB);
    runGerrit("RebuildNoteDb", "-d", sitePath, "--show-stack-trace");
  }

  private void initSite() throws Exception {
    runGerrit(
        "init",
        "-d",
        sitePath,
        "--batch",
        "--no-auto-start",
        "--skip-plugins",
        "--show-stack-trace");
  }

  private static void runGerrit(String... args) throws Exception {
    assertThat(GerritLauncher.mainImpl(args)).isEqualTo(0);
  }

  private void setNotesMigrationState(NotesMigrationState state) throws Exception {
    gerritConfig.load();
    ConfigNotesMigration.setConfigValues(gerritConfig, state.migration());
    gerritConfig.save();
  }
}
