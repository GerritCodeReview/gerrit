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

import static org.junit.Assert.assertEquals;

import com.google.common.io.Files;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.TempFileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class RebuildNotedbIT {
  private File sitePath;

  @Before
  public void createTempDirectory() throws Exception {
    sitePath = TempFileUtil.createTempDirectory();
  }

  @After
  public void destroySite() throws Exception {
    if (sitePath != null) {
      TempFileUtil.cleanup();
    }
  }

  @Test
  public void reindexEmptySite() throws Exception {
    initSite();
    Files.append(NotesMigration.allEnabledConfig().toText(),
        new File(sitePath.toString(), "etc/gerrit.config"),
        StandardCharsets.UTF_8);
    runGerrit("RebuildNotedb", "-d", sitePath.toString(),
        "--show-stack-trace");
  }

  private void initSite() throws Exception {
    runGerrit("init", "-d", sitePath.getPath(),
        "--batch", "--no-auto-start", "--skip-plugins", "--show-stack-trace");
  }

  private static void runGerrit(String... args) throws Exception {
    assertEquals(0, GerritLauncher.mainImpl(args));
  }
}
