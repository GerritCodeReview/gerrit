// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.Config;

import java.util.List;

/** Cache of the objects that users have recently accessed */
@Singleton
public class RecentlyAccessedCacheImpl implements RecentlyAccessedCache {
  static final String RECENT_NAME = "recent";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(RECENT_NAME, Account.Id.class, RecentlyAccessed.class)
            .maximumWeight(10 << 20)
            .loader(RecentlyAccessedLoader.class);

        bind(RecentlyAccessedCacheImpl.class);
        bind(RecentlyAccessedCache.class).to(RecentlyAccessedCacheImpl.class);
      }
    };
  }

  private final int maxEntries;
  private final LoadingCache<Id, RecentlyAccessed> recentCache;

  @Inject
  public RecentlyAccessedCacheImpl(final @GerritServerConfig Config cfg,
      @Named(RECENT_NAME) LoadingCache<Account.Id, RecentlyAccessed> recentCache) {
    this.maxEntries =
        cfg.getInt("cache", RecentlyAccessedCacheImpl.RECENT_NAME,
            "maxEntries", 10);
    this.recentCache = recentCache;
  }

  public void add(final Account.Id accountId, final Project.NameKey project) {
    final RecentlyAccessed recentlyAccessed =
        recentCache.getUnchecked(accountId);
    recentlyAccessed.add(project);
    recentlyAccessed.limit(maxEntries);
    recentCache.put(accountId, recentlyAccessed);
  }

  public List<Project.NameKey> getProjects(final Account.Id accountId) {
    final RecentlyAccessed recentlyAccessed =
        recentCache.getUnchecked(accountId);
    recentlyAccessed.limit(maxEntries);
    recentCache.put(accountId, recentlyAccessed);
    return recentlyAccessed.getProjects();
  }

  static class RecentlyAccessedLoader extends
      CacheLoader<Account.Id, RecentlyAccessed> {
    @Override
    public RecentlyAccessed load(final Account.Id key) throws Exception {
      return new RecentlyAccessed();
    }
  }
}
