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

package com.google.gerrit.server.account.externalids;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.externalids.ExternalIdCacheImpl.Loader;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.TypeLiteral;
import org.eclipse.jgit.lib.ObjectId;

public class ExternalIdModule extends CacheModule {
  @Override
  protected void configure() {
    cache(
            ExternalIdCacheImpl.CACHE_NAME,
            ObjectId.class,
            new TypeLiteral<ImmutableSetMultimap<Account.Id, ExternalId>>() {})
        // The cached data is potentially pretty large and we are always only interested
        // in the latest value, hence the maximum cache weight is set to 1.
        // This can lead to extra cache loads in case of the following race:
        // 1. thread 1 reads the notes ref at revision A
        // 2. thread 2 updates the notes ref to revision B and stores the derived value
        //    for B in the cache
        // 3. thread 1 attempts to read the data for revision A from the cache, and misses
        // 4. later threads attempt to read at B
        // In this race unneeded reloads are done in step 3 (reload from revision A) and
        // step 4 (reload from revision B, because the value for revision B was lost when the
        // reload from revision A was done, since the cache can hold only one entry).
        // These reloads could be avoided by increasing the cache size to 2. However the race
        // window between reading the ref and looking it up in the cache is small so that
        // it's rare that this race happens. Therefore it's not worth to double the memory
        // usage of this cache, just to avoid this.
        .maximumWeight(1)
        .loader(Loader.class);

    bind(ExternalIdCacheImpl.class);
    bind(ExternalIdCache.class).to(ExternalIdCacheImpl.class);
  }
}
