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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.solr;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;

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

  private final SolrChangeIndex changeIndex;

  @Inject
  IndexVersionCheck(SolrChangeIndex changeIndex) {
    this.changeIndex = changeIndex;
  }

  @Override
  public void start() {
    try {
      if (!changeIndex.isIndexed()) {
        throw new ProvisionException(String.format(
            "wrong index schema version%s", upgrade()));
      }
    } catch (IOException e) {
      throw new ProvisionException("unable to get schema version info", e);
    }
  }

  @Override
  public void stop() {
    // Do nothing.
  }

  private final String upgrade() {
    return "\nRun reindex to rebuild the index:\n"
        + "$ java -jar gerrit.war reindex";
  }
}
