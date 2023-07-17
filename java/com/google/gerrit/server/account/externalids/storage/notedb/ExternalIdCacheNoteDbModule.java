// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import com.google.gerrit.server.account.externalids.ExternalIdCache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.ObjectIdCacheSerializer;
import com.google.inject.TypeLiteral;
import java.time.Duration;
import org.eclipse.jgit.lib.ObjectId;

public class ExternalIdCacheNoteDbModule extends CacheModule {
  @Override
  protected void configure() {
    persist(ExternalIdCacheImpl.CACHE_NAME, ObjectId.class, new TypeLiteral<AllExternalIds>() {})
        // The cached data is potentially pretty large and we are always only interested
        // in the latest value. However, due to a race condition, it is possible for different
        // threads to observe different values of the meta ref, and hence request different keys
        // from the cache. Extend the cache size by 1 to cover this case, but expire the extra
        // object after a short period of time, since it may be a potentially large amount of
        // memory.
        // When loading a new value because the primary data advanced, we want to leverage the old
        // cache state to recompute only what changed. This doesn't affect cache size though as
        // Guava calls the loader first and evicts later on.
        .maximumWeight(2)
        .expireFromMemoryAfterAccess(Duration.ofMinutes(1))
        .diskLimit(-1)
        .version(1)
        .keySerializer(ObjectIdCacheSerializer.INSTANCE)
        .valueSerializer(AllExternalIds.Serializer.INSTANCE);

    bind(ExternalIdCacheImpl.class);
    bind(ExternalIdCache.class).to(ExternalIdCacheImpl.class);
  }
}
