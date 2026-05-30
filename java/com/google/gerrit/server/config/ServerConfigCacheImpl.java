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

package com.google.gerrit.server.config;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.DownloadInfo;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class ServerConfigCacheImpl implements ServerConfigCache {
  private static final String SINGLETON_KEY = "GLOBAL";
  private final Cache<String, ServerConfigData> serverConfigCache;
  private final GerritApi gerritApi;
  private final Provider<DownloadInfo> downloadInfoProvider;
  private final Provider<ThreadLocalRequestContext> requestContextProvider;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_CONFIG, String.class, ServerConfigData.class)
            .expireAfterWrite(Duration.ofMinutes(5));
        bind(ServerConfigCache.class).to(ServerConfigCacheImpl.class).in(Scopes.SINGLETON);
      }
    };
  }

  @VisibleForTesting
  @Inject
  public ServerConfigCacheImpl(
      @Named(ServerConfigCache.CACHE_CONFIG) Cache<String, ServerConfigData> serverConfigCache,
      GerritApi gerritApi,
      Provider<DownloadInfo> downloadInfoProvider,
      Provider<ThreadLocalRequestContext> requestContextProvider) {
    this.serverConfigCache = serverConfigCache;
    this.gerritApi = gerritApi;
    this.downloadInfoProvider = downloadInfoProvider;
    this.requestContextProvider = requestContextProvider;
  }

  @Override
  public ServerConfigData get() throws ExecutionException {
    ServerConfigData cachedConfig =
        serverConfigCache.get(
            SINGLETON_KEY,
            () -> {
              try (AutoCloseable unused =
                  new ManualRequestContext(new AnonymousUser(), requestContextProvider.get())) {
                return new ServerConfigData(
                    gerritApi.config().server().getInfo(),
                    gerritApi.config().server().getVersion());
              }
            });

    if (!requestContextProvider.get().getContext().getUser().isIdentifiedUser()) {
      return cachedConfig;
    }

    ServerInfo serverInfo = new ServerInfo();
    serverInfo.accounts = cachedConfig.serverInfo().accounts;
    serverInfo.auth = cachedConfig.serverInfo().auth;
    serverInfo.change = cachedConfig.serverInfo().change;
    serverInfo.gerrit = cachedConfig.serverInfo().gerrit;
    serverInfo.groups = cachedConfig.serverInfo().groups;
    serverInfo.noteDbEnabled = cachedConfig.serverInfo().noteDbEnabled;
    serverInfo.plugin = cachedConfig.serverInfo().plugin;
    serverInfo.sshd = cachedConfig.serverInfo().sshd;
    serverInfo.suggest = cachedConfig.serverInfo().suggest;
    serverInfo.user = cachedConfig.serverInfo().user;
    serverInfo.receive = cachedConfig.serverInfo().receive;
    serverInfo.defaultTheme = cachedConfig.serverInfo().defaultTheme;
    serverInfo.submitRequirementDashboardColumns =
        cachedConfig.serverInfo().submitRequirementDashboardColumns;
    serverInfo.dashboardShowAllLabels = cachedConfig.serverInfo().dashboardShowAllLabels;
    serverInfo.metadata = cachedConfig.serverInfo().metadata;
    serverInfo.download = downloadInfoProvider.get();

    return new ServerConfigData(serverInfo, cachedConfig.serverVersion());
  }
}
