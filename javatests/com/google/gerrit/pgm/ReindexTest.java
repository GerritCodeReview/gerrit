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
import static com.google.gerrit.truth.ExitCodeSubject.exitCode;

import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Rule;
import org.junit.Test;

public class ReindexTest {
  @Rule public SiteRule siteRule = new SiteRule();

  @Test
  public void reindexListRunsProperly() throws Exception {
    FileBasedConfig config =
        new FileBasedConfig(siteRule.getSitePaths().gerrit_config.toFile(), FS.DETECTED);
    config.setString("database", null, "type", "h2");
    config.save();

    siteRule.initSite();

    Reindex reindex = new Reindex();
    int exitCode =
        reindex.main(
            new String[] {"--site-path", siteRule.getSitePaths().site_path.toString(), "--list"});

    assertAbout(exitCode()).that(exitCode).isSuccessful();
  }

  @Test
  public void reindexRunsProperlyWithH2Database() throws Exception {
    FileBasedConfig config =
        new FileBasedConfig(siteRule.getSitePaths().gerrit_config.toFile(), FS.DETECTED);
    config.setString("database", null, "type", "h2");
    config.save();

    siteRule.initSite();

    Reindex reindex = new Reindex();
    int exitCode =
        reindex.main(new String[] {"--site-path", siteRule.getSitePaths().site_path.toString()});

    assertAbout(exitCode()).that(exitCode).isSuccessful();
  }
}
