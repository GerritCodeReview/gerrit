// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class LogFileManagerTest {

  @Test
  public void testLogFilePattern() throws Exception {
    List<String> filenamesWithDate =
        List.of(
            "error_log.2024-01-01",
            "error_log.2024-01-01.gz",
            "error_log.json.2024-01-01",
            "error_log.json.2024-01-01.gz",
            "sshd_log.2024-01-01",
            "httpd_log.2024-01-01");

    List<String> filenamesWithoutDate =
        List.of(
            "error_log",
            "error_log.gz",
            "error_log.json",
            "error_log.json.gz",
            "sshd_log",
            "httpd_log");

    LogFileManager manager =
        new LogFileManager(new SitePaths(Path.of("/gerrit")), new LogConfig(new Config()));
    Instant expected = Instant.parse("2024-01-01T00:00:00.00Z");
    for (String filename : filenamesWithDate) {
      assertThat(manager.getDateFromFilename(Path.of(filename)).get()).isEqualTo(expected);
    }

    for (String filename : filenamesWithoutDate) {
      assertThat(manager.getDateFromFilename(Path.of(filename)).isEmpty()).isTrue();
    }
  }
}
