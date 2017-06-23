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
import static com.google.common.truth.Truth8.assertThat;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.GerritServer;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.util.ManualRequestContext;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

@NoHttpd
public class ReindexIT extends StandaloneSiteTest {
  @Test
  public void reindexFromScratch() throws Exception {
    Project.NameKey project = new Project.NameKey("project");
    String changeId;
    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server)) {
      GerritApi gApi = server.getTestInjector().getInstance(GerritApi.class);
      gApi.projects().create("project");

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      changeId = gApi.changes().create(in).info().changeId;
    }

    FileUtils.deleteDirectory(sitePaths.index_dir.toFile());
    Files.createDirectory(sitePaths.index_dir);

    try (GerritServer server = startServer()) {
      fail("expected server startup to fail");
    } catch (GerritServer.StartupException e) {
      // Expected.
    }

    runGerrit("reindex", "-d", sitePaths.site_path.toString(), "--show-stack-trace");

    try (GerritServer server = startServer();
        ManualRequestContext ctx = openContext(server)) {
      GerritApi gApi = server.getTestInjector().getInstance(GerritApi.class);
      assertThat(gApi.changes().query("message:Test").get().stream().map(c -> c.changeId))
          .containsExactly(changeId);
    }
  }
}
