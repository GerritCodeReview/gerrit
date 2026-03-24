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
import com.google.common.base.Throwables;
import com.google.common.base.Ticker;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.restapi.config.GetServerInfo;
import com.google.gerrit.server.restapi.config.GetVersion;
import com.google.gerrit.server.restapi.config.ListTopMenus;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

  private final Cache<String, ServerInfo> serverInfoCache;
  private final Cache<String, String> versionCache;
  private final Cache<String, List<TopMenu.MenuEntry>> topMenusCache;

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
        CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).ticker(ticker).build();

    this.versionCache =
        CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).ticker(ticker).build();

    this.topMenusCache =
        CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).ticker(ticker).build();
  }

  public ServerInfo getInfo() throws RestApiException {
    try {
      return serverInfoCache.get(
          "info", () -> getServerInfoProvider.get().apply(new ConfigResource()).value());
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      throw RestApiException.wrap("Failed to fetch server info", (Exception) e.getCause());
    }
  }

  public String getVersion() throws RestApiException {
    try {
      return versionCache.get(
          "version", () -> (String) getVersionProvider.get().apply(new ConfigResource()).value());
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      throw RestApiException.wrap("Failed to fetch server version", (Exception) e.getCause());
    }
  }

  public List<TopMenu.MenuEntry> getTopMenus() throws RestApiException {
    try {
      return topMenusCache.get(
          "topMenus", () -> listTopMenusProvider.get().apply(new ConfigResource()).value());
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      throw RestApiException.wrap("Failed to fetch server top menus", (Exception) e.getCause());
    }
  }
}
