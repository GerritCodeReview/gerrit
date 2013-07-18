// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.InitPlugins.installPlugin;
import static com.google.inject.Stage.PRODUCTION;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.secure.DefaultSecureStore;
import com.google.gerrit.common.secure.SecureStore;
import com.google.gerrit.common.secure.SecureStoreLoadUtil;
import com.google.gerrit.pgm.SiteModule;
import com.google.gerrit.pgm.init.InitPlugins.PluginData;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InitSecureStore {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final InitFlags flags;
  private final List<PluginData> plugins;
  private final List<String> installPlugins;

  @Inject
  InitSecureStore(ConsoleUI ui,
      SitePaths site,
      InitFlags flags,
      List<PluginData> plugins,
      @InstallPlugins List<String> installPlugins) {
    this.ui = ui;
    this.site = site;
    this.flags = flags;
    this.plugins = plugins;
    this.installPlugins = installPlugins;
  }

  public Injector init() throws Exception {
    Map<String, SecureStoreLoadUtil.Metadata> secureStores =
        getAllSecureStores();
    Set<String> secureStoreNames = Sets.newHashSet(secureStores.keySet());
    if (secureStoreNames.isEmpty()) {
      return createInjectorWithCustomSecureStore(DefaultSecureStore.class);
    }
    secureStoreNames.add(DefaultSecureStore.NAME);
    String currentProvider = getCurrentSecureStoreName();
    String secureStoreName =
        ui.readString(currentProvider, secureStoreNames, "Secure store");
    if (DefaultSecureStore.NAME.equals(secureStoreName)) {
      if (!DefaultSecureStore.NAME.equals(currentProvider)) {
        flags.cfg.unset("gerrit", null, "secureStore");
      }
      return createInjectorWithCustomSecureStore(DefaultSecureStore.class);
    }
    SecureStoreLoadUtil.Metadata selectedPlugin =
        secureStores.get(secureStoreName);
    File installedPlugin = installPlugin(selectedPlugin.jarFile, ui, site);
    flags.cfg.setString("gerrit", null, "secureStore", secureStoreName);
    Class<? extends SecureStore> storeClass =
        selectedPlugin.load(installedPlugin);
    return createInjectorWithCustomSecureStore(storeClass);
  }

  private String getCurrentSecureStoreName() {
    return Objects.firstNonNull(
        flags.cfg.getString("gerrit", null, "secureStore"),
        DefaultSecureStore.NAME);
  }

  private Map<String, SecureStoreLoadUtil.Metadata> getAllSecureStores()
      throws IOException {
    Map<String, SecureStoreLoadUtil.Metadata> secureStores = Maps.newHashMap();
    for (PluginData pluginData : plugins) {
      Set<SecureStoreLoadUtil.Metadata> store =
          SecureStoreLoadUtil.discover(pluginData.pluginFile);
      for (SecureStoreLoadUtil.Metadata metadata : store) {
        secureStores.put(metadata.storeName, metadata);
      }
    }
    return secureStores;
  }

  private Injector createInjectorWithCustomSecureStore(
      final Class<? extends SecureStore> storeClass) throws Exception {
    List<Module> m = Lists.newArrayList();
    m.add(new InitModule());
    m.add(new SiteModule(ui, site.site_path, installPlugins));
    m.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(SecureStore.class).to(storeClass);
        bind(InitFlags.class).toInstance(flags);
      }
    });
    return Guice.createInjector(PRODUCTION, m);
  }
}
