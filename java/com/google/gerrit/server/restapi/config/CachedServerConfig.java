// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Caches global server configuration data to optimize the initial page load.
 *
 * <p>Uses Guava's Cache to memoize configured properties. Note that if the underlying providers
 * throw an exception (e.g., if the backend goes temporarily offline), the exception is NOT cached,
 * and the cache will retry retrieving the information on the next call.
 */
@Singleton
public class CachedServerConfig {
  private final Provider<GetServerInfo> getServerInfoProvider;
  private final Provider<GetVersion> getVersionProvider;
  private final Provider<ListTopMenus> listTopMenusProvider;

  private static final String GLOBAL_CACHE_KEY = "global_cache_key";

  private final LoadingCache<String, ServerInfo> serverInfoCache;
  private final LoadingCache<String, String> versionCache;
  private final LoadingCache<String, List<TopMenu.MenuEntry>> topMenusCache;

  @Inject
  CachedServerConfig(
      Provider<GetServerInfo> getServerInfoProvider,
      Provider<GetVersion> getVersionProvider,
      Provider<ListTopMenus> listTopMenusProvider) {
    this(getServerInfoProvider, getVersionProvider, listTopMenusProvider, Ticker.systemTicker());
  }

  @VisibleForTesting
  CachedServerConfig(
      Provider<GetServerInfo> getServerInfoProvider,
      Provider<GetVersion> getVersionProvider,
      Provider<ListTopMenus> listTopMenusProvider,
      Ticker ticker) {
    this.getServerInfoProvider = getServerInfoProvider;
    this.getVersionProvider = getVersionProvider;
    this.listTopMenusProvider = listTopMenusProvider;

    this.serverInfoCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .ticker(ticker)
            .build(
                new CacheLoader<String, ServerInfo>() {
                  @Override
                  public ServerInfo load(String key) throws Exception {
                    return getServerInfoProvider.get().apply(new ConfigResource()).value();
                  }

                  @Override
                  public ListenableFuture<ServerInfo> reload(String key, ServerInfo oldValue) {
                    return Futures.submitAsync(
                        () -> Futures.immediateFuture(load(key)), ForkJoinPool.commonPool());
                  }
                });

    this.versionCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .ticker(ticker)
            .build(
                new CacheLoader<String, String>() {
                  @Override
                  public String load(String key) throws Exception {
                    return (String) getVersionProvider.get().apply(new ConfigResource()).value();
                  }

                  @Override
                  public ListenableFuture<String> reload(String key, String oldValue) {
                    return Futures.submitAsync(
                        () -> Futures.immediateFuture(load(key)), ForkJoinPool.commonPool());
                  }
                });

    this.topMenusCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .ticker(ticker)
            .build(
                new CacheLoader<String, List<TopMenu.MenuEntry>>() {
                  @Override
                  public List<TopMenu.MenuEntry> load(String key) throws Exception {
                    return listTopMenusProvider.get().apply(new ConfigResource()).value();
                  }

                  @Override
                  public ListenableFuture<List<TopMenu.MenuEntry>> reload(
                      String key, List<TopMenu.MenuEntry> oldValue) {
                    return Futures.submitAsync(
                        () -> Futures.immediateFuture(load(key)), ForkJoinPool.commonPool());
                  }
                });
  }

  public ServerInfo getInfo() throws RestApiException {
    try {
      return serverInfoCache.get(GLOBAL_CACHE_KEY);
    } catch (ExecutionException e) {
      throw RestApiException.wrap("Failed to fetch server info", (Exception) e.getCause());
    }
  }

  public String getVersion() throws RestApiException {
    try {
      return versionCache.get(GLOBAL_CACHE_KEY);
    } catch (ExecutionException e) {
      throw RestApiException.wrap("Failed to fetch server version", (Exception) e.getCause());
    }
  }

  public List<TopMenu.MenuEntry> getTopMenus() throws RestApiException {
    try {
      return topMenusCache.get(GLOBAL_CACHE_KEY);
    } catch (ExecutionException e) {
      throw RestApiException.wrap("Failed to fetch server top menus", (Exception) e.getCause());
    }
  }
}
