// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.common.primitives.Ints;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class GerritIndexStatus {
  private static final String SECTION = "index";
  private static final String KEY_READY = "ready";

  private final FileBasedConfig cfg;

  public GerritIndexStatus(SitePaths sitePaths) throws ConfigInvalidException, IOException {
    cfg =
        new FileBasedConfig(
            sitePaths.index_dir.resolve("gerrit_index.config").toFile(), FS.detect());
    cfg.load();
    convertLegacyConfig();
  }

  public void setReady(String indexName, int version, boolean ready) {
    cfg.setBoolean(SECTION, indexDirName(indexName, version), KEY_READY, ready);
  }

  public boolean getReady(String indexName, int version) {
    return cfg.getBoolean(SECTION, indexDirName(indexName, version), KEY_READY, false);
  }

  public void save() throws IOException {
    cfg.save();
  }

  private void convertLegacyConfig() throws IOException {
    boolean dirty = false;
    // Convert legacy [index "25"] to modern [index "changes_0025"].
    for (String subsection : cfg.getSubsections(SECTION)) {
      Integer v = Ints.tryParse(subsection);
      if (v != null) {
        String ready = cfg.getString(SECTION, subsection, KEY_READY);
        if (ready != null) {
          dirty = false;
          cfg.unset(SECTION, subsection, KEY_READY);
          cfg.setString(SECTION, indexDirName(ChangeSchemaDefinitions.NAME, v), KEY_READY, ready);
        }
      }
    }
    if (dirty) {
      cfg.save();
    }
  }

  private static String indexDirName(String indexName, int version) {
    return String.format("%s_%04d", indexName, version);
  }
}
