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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.ServerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class CachedServerConfig {
  private final Supplier<ServerInfo> serverInfoSupplier;
  private final Supplier<String> versionSupplier;
  private final Supplier<List<TopMenu.MenuEntry>> topMenusSupplier;

  @Inject
  CachedServerConfig(GerritApi gerritApi) {
    this.serverInfoSupplier =
        Suppliers.memoizeWithExpiration(
            () -> {
              try {
                return gerritApi.config().server().getInfo();
              } catch (RestApiException e) {
                throw new IllegalStateException("Failed to fetch server info", e);
              }
            },
            5,
            TimeUnit.MINUTES);

    this.versionSupplier =
        Suppliers.memoizeWithExpiration(
            () -> {
              try {
                return gerritApi.config().server().getVersion();
              } catch (RestApiException e) {
                throw new IllegalStateException("Failed to fetch server version", e);
              }
            },
            5,
            TimeUnit.MINUTES);

    this.topMenusSupplier =
        Suppliers.memoizeWithExpiration(
            () -> {
              try {
                return gerritApi.config().server().topMenus();
              } catch (RestApiException e) {
                throw new IllegalStateException("Failed to fetch server top menus", e);
              }
            },
            5,
            TimeUnit.MINUTES);
  }

  public ServerInfo getInfo() {
    return serverInfoSupplier.get();
  }

  public String getVersion() {
    return versionSupplier.get();
  }

  public List<TopMenu.MenuEntry> topMenus() {
    return topMenusSupplier.get();
  }
}
