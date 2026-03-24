// Copyright (C) 2019 The Android Open Source Project
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
import com.google.inject.TypeLiteral;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ServerConfigCacheImpl {
  public static final String CACHE_INFO = "server_info";
  public static final String CACHE_VERSION = "server_version";
  public static final String CACHE_MENUS = "server_top_menus";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_INFO, String.class, ServerInfo.class).expireAfterWrite(5, TimeUnit.MINUTES);
        cache(CACHE_VERSION, String.class, String.class).expireAfterWrite(5, TimeUnit.MINUTES);
        cache(CACHE_MENUS, String.class, new TypeLiteral<List<TopMenu.MenuEntry>>() {})
            .expireAfterWrite(5, TimeUnit.MINUTES);
      }
    };
  }
}
