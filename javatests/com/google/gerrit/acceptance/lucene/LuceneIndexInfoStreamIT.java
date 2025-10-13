// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.lucene;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import java.io.File;
import org.junit.Test;

public class LuceneIndexInfoStreamIT extends AbstractDaemonTest {

  @Test
  @GerritConfig(name = "index.changes_open.persistInfoStream", value = "true")
  @GerritConfig(name = "index.type", value = "lucene")
  public void luceneInfoStreamFileIsCreatedAndIsNotEmptyWhenEnabled() throws Exception {
    createChange();

    File[] luceneChangesIndexfolders =
        sitePaths
            .logs_dir
            .toFile()
            .listFiles((dir, name) -> name.equals("changes_open_lucene_log"));
    assertThat(luceneChangesIndexfolders).isNotNull();
    assertThat(luceneChangesIndexfolders).hasLength(1);
  }

  @Test
  @GerritConfig(name = "index.type", value = "lucene")
  public void luceneInfoStreamFileIsNotCreatedWhenDisabled() throws Exception {
    createChange();

    File[] luceneChangesIndexfolders =
        sitePaths
            .logs_dir
            .toFile()
            .listFiles((dir, name) -> name.startsWith("changes_open_lucene_log"));
    assertThat(luceneChangesIndexfolders).isNotNull();
    assertThat(luceneChangesIndexfolders).isEmpty();
  }
}
