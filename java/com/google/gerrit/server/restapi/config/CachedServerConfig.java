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

import static com.google.template.soy.data.ordainers.GsonOrdainer.serializeObject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gson.Gson;
import com.google.template.soy.data.SanitizedContent;
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
  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  public static class ServerConfigData {
    public final SanitizedContent serverInfoJson;
    public final SanitizedContent serverVersionJson;
    public final SanitizedContent topMenusJson;

    public ServerConfigData(
        SanitizedContent serverInfoJson,
        SanitizedContent serverVersionJson,
        SanitizedContent topMenusJson) {
      this.serverInfoJson = serverInfoJson;
      this.serverVersionJson = serverVersionJson;
      this.topMenusJson = topMenusJson;
    }
  }

  private final LoadingCache<String, ServerConfigData> configCache;

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

    this.configCache =
        CacheBuilder.newBuilder()
            .refreshAfterWrite(5, TimeUnit.MINUTES)
            .ticker(ticker)
            .build(
                new CacheLoader<String, ServerConfigData>() {
                  @Override
                  public ServerConfigData load(String key) throws Exception {
                    ConfigResource resource = new ConfigResource();
                    return new ServerConfigData(
                        serializeObject(GSON, getServerInfoProvider.get().apply(resource).value()),
                        serializeObject(GSON, getVersionProvider.get().apply(resource).value()),
                        serializeObject(GSON, listTopMenusProvider.get().apply(resource).value()));
                  }

                  @Override
                  public ListenableFuture<ServerConfigData> reload(
                      String key, ServerConfigData oldValue) {
                    return Futures.submitAsync(
                        () -> Futures.immediateFuture(load(key)), ForkJoinPool.commonPool());
                  }
                });
  }

  public ServerConfigData getServerConfig() throws RestApiException {
    try {
      return configCache.get(GLOBAL_CACHE_KEY);
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      throw RestApiException.wrap(
          "Failed to fetch server config",
          e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
    }
  }
}
