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

import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;

public class InitTest extends AbstractProgramTest {
  /**
   * Checks that the init command creates the important files and directories. etc and gerrit_config
   * are excluded, as they are created by AbstractProgramTest
   */
  @Test
  public void testInitRunsProperly() throws Exception {
    Collection<Path> pathsToCheck =
        ImmutableList.<Path>builder()
            .add(site.bin_dir)
            .add(site.lib_dir)
            .add(site.tmp_dir)
            .add(site.logs_dir)
            .add(site.plugins_dir)
            .add(site.db_dir)
            .add(site.data_dir)
            .add(site.mail_dir)
            .add(site.static_dir)
            .add(site.index_dir)
            .add(site.secure_config)
            .build();

    // Safeguard: before running the init command, none of the above files should exist
    for (Path path : pathsToCheck) {
      assertThat(path.toFile().exists())
          .named(site.site_path.relativize(path).getFileName() + " exists")
          .isFalse();
    }

    Init ls = new Init(site.site_path);
    int exitCode = ls.main(new String[] {"-b"});
    assertThat(exitCode).named("Init exit code").isEqualTo(0);

    for (Path path : pathsToCheck) {
      assertThat(path.toFile().exists())
          .named(site.site_path.relativize(path).getFileName() + " exists")
          .isTrue();
    }
  }
}
