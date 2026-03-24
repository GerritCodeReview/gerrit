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

import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Module;
import java.time.Duration;
import java.util.List;

public class ServerConfigCacheImpl {
  public static final String CACHE_CONFIG = "server_config";
  public static final String SINGLETON_KEY = "GLOBAL";

  public static class ServerConfigData {
    public final ServerInfo serverInfo;
    public final String serverVersion;
    public final List<TopMenu.MenuEntry> topMenus;

    public ServerConfigData(
        ServerInfo serverInfo, String serverVersion, List<TopMenu.MenuEntry> topMenus) {
      this.serverInfo = serverInfo;
      this.serverVersion = serverVersion;
      this.topMenus = topMenus;
    }
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_CONFIG, String.class, ServerConfigData.class)
            .expireAfterWrite(Duration.ofMinutes(5));
      }
    };
  }
}
