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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.server.config.SitePaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Before;
import org.junit.Ignore;

/** Base test for batch programs, setting up a test_site and a gerrit.config file */
@Ignore
@UseLocalDisk
public abstract class AbstractProgramTest extends LocalDiskRepositoryTestCase {
  protected SitePaths site;
  private ConfigBuiler configBuilder;

  @Before
  public void setUpSiteData() throws IOException {
    Path p = createWorkRepository().getWorkTree().toPath().resolve("test_site");
    site = new SitePaths(p);

    assertThat(site.isNew).named("Test Site directory is empty");

    FileUtil.mkdirsOrDie(site.etc_dir, "Failed to create");

    configBuilder = new ConfigBuiler(site.gerrit_config.toFile());
    configBuilder.setString("gerrit", null, "basePath", getSitePath());
  }

  protected String getSitePath() {
    return site.site_path.toString();
  }

  protected ConfigBuiler configBuiler() {
    return configBuilder;
  }

  protected void initSite() throws Exception {
    Init init = new Init(site.site_path);
    int exitCode = init.main(new String[] {"-b"});

    assertThat(exitCode).named("initSite exit code").isEqualTo(0);
  }

  static class ConfigBuiler {
    private final FileBasedConfig config;

    ConfigBuiler(File gerritConfig) {
      config = new FileBasedConfig(gerritConfig, FS.DETECTED);
    }

    public ConfigBuiler setString(String section, String subsection, String name, String value) {
      config.setString(section, subsection, name, value);
      return this;
    }

    public void save() throws IOException {
      config.save();
    }
  }
}
