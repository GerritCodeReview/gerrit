// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.ExitCodeSubject.exitCode;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;

public class InitTest {
  @Rule public SiteRule siteRule = new SiteRule();

  /**
   * Checks that the init command creates the important files and directories. etc is excluded, as
   * it is created by the SiteRule.
   */
  @Test
  public void initCreatesTheDirectory() throws Exception {
    SitePaths site = siteRule.getSitePaths();

    ImmutableList<Path> pathsToCheck =
        ImmutableList.of(
            site.bin_dir,
            site.lib_dir,
            site.tmp_dir,
            site.logs_dir,
            site.gerrit_config,
            site.plugins_dir,
            site.db_dir,
            site.data_dir,
            site.mail_dir,
            site.static_dir,
            site.index_dir,
            site.secure_config);

    // Safeguard: before running the init command, none of the above files should exist
    for (Path path : pathsToCheck) {
      assertThat(Files.exists(path)).named(site.site_path.getFileName() + " exists").isFalse();
    }

    new Init(site.site_path).main(new String[] {"--batch"});

    for (Path path : pathsToCheck) {
      assertThat(Files.exists(path)).named(site.site_path.getFileName() + " exists").isTrue();
    }
  }

  @Test
  public void initRunsProperly() throws Exception {
    Init init = new Init(siteRule.getSitePaths().site_path);
    int exitCode = init.main(new String[] {"--batch"});
    assertAbout(exitCode()).that(exitCode).isSuccessful();
  }
}
