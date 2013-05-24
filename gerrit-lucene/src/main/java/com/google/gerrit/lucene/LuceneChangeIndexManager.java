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

import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

@Singleton
class LuceneChangeIndexManager implements ChangeIndex.Manager,
    LifecycleListener {
  private final LoadingCache<String, LuceneChangeIndex> indexes;

  @Inject
  LuceneChangeIndexManager(final SitePaths sitePaths, final FillArgs fillArgs) {
    indexes = CacheBuilder.newBuilder().build(
      new CacheLoader<String, LuceneChangeIndex>() {
        @Override
        public LuceneChangeIndex load(String key) throws IOException {
          return new LuceneChangeIndex(
              new File(sitePaths.index_dir, key), fillArgs);
        }
      });
  }

  @Override
  public void start() {
    // Do nothing.
  }

  @Override
  public void stop() {
    for (LuceneChangeIndex index : indexes.asMap().values()) {
      index.close();
    }
  }

  @Override
  public LuceneChangeIndex get(String name) throws IOException {
    try {
      return indexes.get(name);
    } catch (ExecutionException e) {
      Throwables.propagateIfInstanceOf(e.getCause(), IOException.class);
      throw new IOException(e);
    }
  }
}
