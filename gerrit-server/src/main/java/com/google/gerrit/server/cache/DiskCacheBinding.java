// Copyright (C) 2009 The Android Open Source Project
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

import java.util.concurrent.TimeUnit;

/** Configure a cache declared within a {@link CacheModule} instance. */
public interface DiskCacheBinding<K, V> {
  /** Set the number of objects to cache in memory. */
  public DiskCacheBinding<K, V> memoryLimit(int objects);

  /** Set the number of objects to cache in memory. */
  public DiskCacheBinding<K, V> diskLimit(int objects);

  /** Set the time an element lives before being expired. */
  public DiskCacheBinding<K, V> maxAge(long duration, TimeUnit durationUnits);

  /** Populate the cache with items from the EntryCreator. */
  public DiskCacheBinding<K, V> populateWith(Class<? extends EntryCreator<K, V>> creator);
}
