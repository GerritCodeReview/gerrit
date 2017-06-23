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

import static com.google.common.truth.Truth8.assertThat;

import com.google.common.io.MoreFiles;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.reviewdb.client.Project;
import java.nio.file.Files;
import org.junit.Test;

@NoHttpd
public class ReindexIT extends StandaloneSiteTest {
  @Test
  public void reindexFromScratch() throws Exception {
    Project.NameKey project = new Project.NameKey("project");
    String changeId;
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      gApi.projects().create("project");

      ChangeInput in = new ChangeInput();
      in.project = project.get();
      in.branch = "master";
      in.subject = "Test change";
      in.newBranch = true;
      changeId = gApi.changes().create(in).info().changeId;
    }

    MoreFiles.deleteRecursively(sitePaths.index_dir);
    Files.createDirectory(sitePaths.index_dir);
    assertServerStartupFails();

    runGerrit("reindex", "-d", sitePaths.site_path.toString(), "--show-stack-trace");

    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);
      assertThat(gApi.changes().query("message:Test").get().stream().map(c -> c.changeId))
          .containsExactly(changeId);
    }
  }
}
