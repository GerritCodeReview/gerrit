// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.SortedSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class AxisProjectNameMappingCache {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final LoadingCache<Boolean, Iterable<NameKey>> cache;

  @Inject
  AxisProjectNameMappingCache(final LocalDiskRepositoryManager repoManager) {
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(1)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build(
                new CacheLoader<Boolean, Iterable<Project.NameKey>>() {
                  @Override
                  public SortedSet<Project.NameKey> load(Boolean key) throws Exception {
                    return repoManager.list();
                  }
                });
  }

  Iterable<Project.NameKey> get() {
    try {
      return cache.get(true);
    } catch (ExecutionException e) {
      logger.atWarning().withCause(e).log("Cannot fetch projects from cache");
      return Collections.emptySet();
    }
  }
}
