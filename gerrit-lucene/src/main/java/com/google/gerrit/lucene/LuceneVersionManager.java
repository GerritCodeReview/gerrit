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

package com.google.gerrit.lucene;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

class LuceneVersionManager implements LifecycleListener {
  private static final Logger log =
      LoggerFactory.getLogger(LuceneVersionManager.class);

  private static final String CHANGES_PREFIX = "changes_";

  static File getDir(SitePaths sitePaths, Schema<ChangeData> schema) {
    return new File(sitePaths.index_dir,
        String.format("%s%04d", CHANGES_PREFIX, schema.getVersion()));
  }

  private final SitePaths sitePaths;
  private final IndexCollection indexes;
  private final LuceneChangeIndex.Factory indexFactory;

  @Inject
  LuceneVersionManager(
      SitePaths sitePaths,
      LuceneChangeIndex.Factory indexFactory,
      IndexCollection indexes) {
    this.sitePaths = sitePaths;
    this.indexFactory = indexFactory;
    this.indexes = indexes;
  }

  @Override
  public void start() {
    Map<Integer, Schema<ChangeData>> schemas = ChangeSchemas.ALL;
    LuceneChangeIndex latest = null;
    for (File f : sitePaths.index_dir.listFiles()) {
      String p = f.getAbsolutePath();
      if (!f.getName().startsWith(CHANGES_PREFIX)) {
        log.warn("Unrecognized file in index directory: %s", p);
        continue;
      }
      if (!f.isDirectory()) {
        log.warn("Not a directory: %s", p);
        continue;
      }
      String versionStr = f.getName().substring(CHANGES_PREFIX.length());
      Integer version = Ints.tryParse(versionStr);
      if (version == null) {
        log.warn("Invalid version in index directory: %s", p);
        continue;
      }
      Schema<ChangeData> schema = schemas.get(version);
      if (schema == null) {
        log.warn("Unsupported version in index directory: %s", p);
        continue;
      }
      LuceneChangeIndex index = indexFactory.create(schema);
      indexes.addWriteIndex(index);
      if (latest == null
          || schema.getVersion() < latest.getSchema().getVersion()) {
        latest = index;
      }
    }
    checkState(latest != null, "No supported index versions found in %s",
        sitePaths.index_dir.getAbsolutePath());
    indexes.setSearchIndex(latest);
  }

  @Override
  public void stop() {
  }
}
