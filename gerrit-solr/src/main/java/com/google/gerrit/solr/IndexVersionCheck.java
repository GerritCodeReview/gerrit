// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.solr;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.Map;

class IndexVersionCheck implements LifecycleListener {
  public static final Map<String, Integer> SCHEMA_VERSIONS = ImmutableMap.of(
      SolrChangeIndex.CHANGES_OPEN, ChangeSchemas.getLatest().getVersion(),
      SolrChangeIndex.CHANGES_CLOSED, ChangeSchemas.getLatest().getVersion());

  public static File solrIndexConfig(SitePaths sitePaths) {
    return new File(sitePaths.index_dir, "gerrit_index.config");
  }

  private final SitePaths sitePaths;

  @Inject
  IndexVersionCheck(SitePaths sitePaths) {
    this.sitePaths = sitePaths;
  }

  @Override
  public void start() {
    // TODO Query schema version from a special meta-document
    File file = solrIndexConfig(sitePaths);
    try {
      FileBasedConfig cfg = new FileBasedConfig(file, FS.detect());
      cfg.load();
      for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
        int schemaVersion = cfg.getInt("index", e.getKey(), "schemaVersion", 0);
        if (schemaVersion != e.getValue()) {
          throw new ProvisionException(String.format(
              "wrong index schema version for \"%s\": expected %d, found %d%s",
              e.getKey(), e.getValue(), schemaVersion, upgrade()));
        }
      }
    } catch (IOException e) {
      throw new ProvisionException("unable to read " + file);
    } catch (ConfigInvalidException e) {
      throw new ProvisionException("invalid config file " + file);
    }
  }

  @Override
  public void stop() {
    // Do nothing.
  }

  private final String upgrade() {
    return "\nRun reindex to rebuild the index:\n"
        + "$ java -jar gerrit.war reindex -d "
        + sitePaths.site_path.getAbsolutePath();
  }
}
