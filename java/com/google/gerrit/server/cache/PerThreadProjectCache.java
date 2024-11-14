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

package com.google.gerrit.server.cache;

import com.google.common.collect.Maps;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.Project;
import java.util.Map;
import java.util.function.Supplier;

/**
 * To prevent OOM errors on requests that would cache a lot of objects, this class enforces an
 * internal limit after which no new elements are cached. All {@code computeIfAbsentWithinLimit}
 * calls are served by invoking the {@code Supplier} after that.
 */
public class PerThreadProjectCache {
  private static final PerThreadCache.Key<PerThreadProjectCache> PER_THREAD_PROJECT_CACHE_KEY =
      PerThreadCache.Key.create(PerThreadProjectCache.class);

  /**
   * Cache at maximum 25 values per thread. This value was chosen arbitrarily. Some endpoints (like
   * ListProjects) break the assumption that the data cached in a request is limited. To prevent
   * this class from accumulating an unbound number of objects, we enforce this limit.
   */
  private static final int PER_THREAD_PROJECT_CACHE_SIZE = 25;

  private final Map<PerThreadCache.Key<Project.NameKey>, Object> valueByNameKey =
      Maps.newHashMapWithExpectedSize(PER_THREAD_PROJECT_CACHE_SIZE);

  private PerThreadProjectCache() {}

  @CanIgnoreReturnValue
  public static <T> T getOrCompute(PerThreadCache.Key<Project.NameKey> key, Supplier<T> loader) {
    PerThreadCache perThreadCache = PerThreadCache.get();
    if (perThreadCache != null) {
      PerThreadProjectCache perThreadProjectCache =
          perThreadCache.get(PER_THREAD_PROJECT_CACHE_KEY, PerThreadProjectCache::new);
      return perThreadProjectCache.computeIfAbsentWithinLimit(key, loader);
    }
    return loader.get();
  }

  protected <T> T computeIfAbsentWithinLimit(
      PerThreadCache.Key<Project.NameKey> key, Supplier<T> loader) {
    @SuppressWarnings("unchecked")
    T value = (T) valueByNameKey.get(key);
    if (value == null) {
      value = loader.get();
      if (valueByNameKey.size() < PER_THREAD_PROJECT_CACHE_SIZE) {
        valueByNameKey.put(key, value);
      }
    }
    return value;
  }
}
