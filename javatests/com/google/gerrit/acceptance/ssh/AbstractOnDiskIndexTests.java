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

package com.google.gerrit.acceptance.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import org.junit.Test;

@UseLocalDisk
public abstract class AbstractOnDiskIndexTests extends AbstractIndexTests {
  @Inject private VersionManager versionManager;

  @Test
  public void startIndex() throws Exception {
    configureIndex(server.getTestInjector());

    String[] indexes = {"accounts", "changes", "groups", "projects"};
    for (String index : indexes) {
      String cmd = Joiner.on(" ").join("gerrit", "index", "start", index);
      adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();

      versionManager.setLowestIndex(index);
      String result = adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();
      assertEquals("Reindexer started", result.trim());

      boolean reindexing = true;
      while (reindexing) {
        adminSshSession.exec(cmd);
        if (adminSshSession.getError() != null) {
          assertTrue(
              adminSshSession
                  .getError()
                  .trim()
                  .equals("fatal: Failed to start reindexer: Reindexer is already running."));
        } else {
          reindexing = false;
        }
      }
      adminSshSession.assertSuccess();
      assertEquals("Nothing to reindex, index is already the latest version", result.trim());
    }
  }

  @Test
  public void activateIndex() throws Exception {
    configureIndex(server.getTestInjector());

    String[] indexes = {"accounts", "changes", "groups"};
    for (String index : indexes) {
      String cmd = Joiner.on(" ").join("gerrit", "index", "activate", index);
      adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();

      versionManager.setLowestIndex(index);
      versionManager.setIndex(index);
      String result = adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();
      assertTrue(result.contains("Activated latest index"));
    }
  }
}
