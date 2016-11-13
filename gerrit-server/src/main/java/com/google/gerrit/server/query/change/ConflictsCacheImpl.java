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

package com.google.gerrit.server.query.change;

import com.google.common.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
public class ConflictsCacheImpl implements ConflictsCache {
  public static final String NAME = "conflicts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(NAME, ConflictKey.class, Boolean.class).maximumWeight(37400);
        bind(ConflictsCache.class).to(ConflictsCacheImpl.class);
      }
    };
  }

  private final Cache<ConflictKey, Boolean> conflictsCache;

  @Inject
  public ConflictsCacheImpl(@Named(NAME) Cache<ConflictKey, Boolean> conflictsCache) {
    this.conflictsCache = conflictsCache;
  }

  @Override
  public void put(ConflictKey key, Boolean value) {
    conflictsCache.put(key, value);
  }

  @Override
  public Boolean getIfPresent(ConflictKey key) {
    return conflictsCache.getIfPresent(key);
  }
}
