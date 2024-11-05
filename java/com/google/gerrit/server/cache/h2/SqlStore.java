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

package com.google.gerrit.server.cache.h2;

import com.google.common.cache.Cache;
import com.google.gerrit.server.cache.PersistentCache.DiskStats;
import com.google.gerrit.server.cache.h2.H2CacheImpl.ValueHolder;
import java.time.Instant;

public interface SqlStore<K, V> {

  public String getUrl();

  public void open();

  public void close();

  public boolean mightContain(K key);

  public ValueHolder<V> getIfPresent(K key);

  public boolean needsRefresh(Instant created);

  public void put(K key, ValueHolder<V> holder);

  public void invalidate(K key);

  public void invalidateAll();

  public void prune(Cache<K, ?> mem);

  public DiskStats diskStats();
}
