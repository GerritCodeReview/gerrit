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

package com.google.gerrit.acceptance.pgm;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.StandaloneSiteTest;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.launcher.GerritLauncher;
import org.junit.Test;

@UseLocalDisk
public class CopyApprovalsPgmIT extends StandaloneSiteTest {
  @Test
  public void programFinishesNormally() throws Exception {
    // This test checks that we are able to set up the injector for the program and loop over
    // changes. The actual migration logic is tested elsewhere.
    Project.NameKey project = Project.nameKey("reindex-project-test");
    try (ServerContext ctx = startServer()) {
      GerritApi gApi = ctx.getInjector().getInstance(GerritApi.class);

      gApi.projects().create(project.get());

      ChangeInput in = new ChangeInput(project.get(), "master", "Test change");
      in.newBranch = true;
      gApi.changes().create(in);
    }

    int exitCode =
        GerritLauncher.mainImpl(
            new String[] {
              "CopyApprovals", "-d", sitePaths.site_path.toString(), "--show-stack-trace"
            });
    assertThat(exitCode).isEqualTo(0);
  }
}
