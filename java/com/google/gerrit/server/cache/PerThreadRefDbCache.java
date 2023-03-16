// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.server.cache.PerThreadCache;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.RefDatabase;

/** A per request thread cache of RefDatabases by directory (Project). */
public class PerThreadRefDbCache {
  protected static final PerThreadCache.Key<PerThreadRefDbCache> REFDB_CACHE_KEY =
      PerThreadCache.Key.create(PerThreadRefDbCache.class);

  public static RefDatabase getRefDatabase(File path, RefDatabase refDb) {
    if (PerThreadCache.get() != null) {
      return PerThreadCache.get()
          .get(REFDB_CACHE_KEY, PerThreadRefDbCache::new)
          .computeIfAbsent(path, p -> ((RefDirectory) refDb).createSnapshottingRefDirectory());
    }
    return refDb;
  }

  protected final Map<File, RefDatabase> refDbByRefsDir = new HashMap<>();

  public RefDatabase computeIfAbsent(
      File path, Function<? super File, ? extends RefDatabase> mappingFunction) {
    return refDbByRefsDir.computeIfAbsent(path, mappingFunction);
  }
}
