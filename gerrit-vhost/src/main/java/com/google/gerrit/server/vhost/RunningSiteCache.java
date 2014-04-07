// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.common.Nullable;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;

import java.util.concurrent.ExecutionException;

import javax.inject.Singleton;

/** Cache of running Gerrit Code Review servers. */
@Singleton
public class RunningSiteCache {
  private final LoadingCache<String, RunningSite> sites;

  @Inject
  RunningSiteCache(
      @GerritGlobalConfig Config cfg,
      final RunningSite.Globals globals) {
    int sz = cfg.getInt("cache", "running_sites", "memoryLimit", 64);

    sites = CacheBuilder.newBuilder()
      .initialCapacity(sz)
      .maximumSize(sz)
      .removalListener(new RemovalListener<String, RunningSite>() {
        @Override
        public void onRemoval(RemovalNotification<String, RunningSite> event) {
          event.getValue().stop();
        }
      })
      .build(new CacheLoader<String, RunningSite>() {
        @Override
        public RunningSite load(String siteName) throws Exception {
          return RunningSite.create(globals, siteName);
        }

        @Override
        public ListenableFuture<RunningSite> reload(
            String siteName, RunningSite oldSite) throws Exception {
          return Futures.immediateFuture(
              RunningSite.create(globals, siteName));
        }
      });
  }

  /**
   *Load a site from the cache, constructing and starting it if not yet loaded.
   *
   * @param siteName the Gerrit server to find the site of.
   * @return the site. Never null.
   * @throws ExecutionException the site cannot be loaded.
   */
  public RunningSite loadSite(String siteName) throws ExecutionException {
    try {
      return sites.get(siteName);
    } catch (UncheckedExecutionException err) {
      throw new ExecutionException(err.getCause());
    }
  }

  /**
   * Locate a site in cache by host name, returning null if not yet loaded.
   *
   * @param siteName the Gerrit server to find the site of.
   * @return the running site's data, null if the site has not been loaded.
   */
  @Nullable
  public RunningSite getIfLoaded(String siteName) {
    return sites.getIfPresent(siteName);
  }
}
