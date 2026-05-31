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

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class ServerConfigCacheImpl implements ServerConfigCache {
  private static final String CACHE_CONFIG = "server_config";
  private static final String SINGLETON_KEY = "GLOBAL";
  private final Cache<String, ServerInfoAndVersion> serverConfigCache;
  private final GerritApi gerritApi;

  public record ServerInfoAndVersion(ServerInfo serverInfo, String serverVersion) {}

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_CONFIG, String.class, ServerInfoAndVersion.class)
            .expireAfterWrite(Duration.ofMinutes(5));
        bind(ServerConfigCache.class).to(ServerConfigCacheImpl.class).in(Scopes.SINGLETON);
      }
    };
  }

  @Inject
  ServerConfigCacheImpl(
      @Named(CACHE_CONFIG) Cache<String, ServerInfoAndVersion> serverConfigCache,
      GerritApi gerritApi) {
    this.serverConfigCache = serverConfigCache;
    this.gerritApi = gerritApi;
  }

  @Override
  public ServerInfoAndVersion get() throws IOException {
    try {
      return serverConfigCache.get(
          SINGLETON_KEY,
          () ->
              new ServerInfoAndVersion(
                  gerritApi.config().server().getInfo(), gerritApi.config().server().getVersion()));
    } catch (ExecutionException e) {
      throw new IOException(e);
    }
  }
}
