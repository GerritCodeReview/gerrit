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
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.PluginData;
import com.google.gerrit.pgm.SiteModule;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreData;
import com.google.gerrit.server.securestore.SecureStoreProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class InitSecureStore {
  private static final String SECURE_STORE_CONFIG_NAME = "secureStore";

  private final ConsoleUI ui;
  private final SitePaths site;
  private final InitFlags flags;
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
    this.installPlugins = installPlugins;
  }

  public Injector init() throws Exception {
    Map<String, SecureStoreData> secureStores = getAllSecureStores();
    Set<String> secureStoreNames = Sets.newTreeSet(secureStores.keySet());
    if (secureStoreNames.isEmpty()) {
      return createInjectorWithDefaultSecureStore();
    }
    secureStoreNames.add(DefaultSecureStore.NAME);
    String currentProvider = getCurrentSecureStoreName();
    String secureStoreName =
        ui.readString(currentProvider, secureStoreNames, "Secure store");
    if (DefaultSecureStore.NAME.equals(secureStoreName)) {
      if (!DefaultSecureStore.NAME.equals(currentProvider)) {
        flags.cfg.unset("gerrit", null, SECURE_STORE_CONFIG_NAME);
      }
      return createInjectorWithDefaultSecureStore();
    }
    SecureStoreData selectedPlugin = secureStores.get(secureStoreName);
    File installedPlugin = installPlugin(selectedPlugin, ui, site);
    flags.cfg.setString("gerrit", null, SECURE_STORE_CONFIG_NAME,
        secureStoreName);
    Class<? extends SecureStore> storeClass =
        selectedPlugin.load(installedPlugin);
    return createInjectorWithCustomSecureStore(storeClass);
  }

  private String getCurrentSecureStoreName() {
    return Objects.firstNonNull(
        flags.cfg.getString("gerrit", null, SECURE_STORE_CONFIG_NAME),
        DefaultSecureStore.NAME);
  }

  private Map<String, SecureStoreData> getAllSecureStores()
      throws InvalidPluginException, ClassNotFoundException, IOException {
    if (Strings.isNullOrEmpty(flags.secureStorePath)) {
      return Collections.emptyMap();
    }
    Map<String, SecureStoreData> secureStores = Maps.newHashMap();
    File plugin = new File(flags.secureStorePath);
    installPlugins.add(plugin.getName().substring(0, plugin.getName().length() - 3)); // remove file extension
    String pluginName = PluginLoader.getPluginName(plugin);
    File tmpPlugin = PluginLoader.storeInTemp(plugin.getName(), new FileInputStream(plugin), site);
    Set<SecureStoreData> stores =
        SecureStoreProvider.findSecureStores(tmpPlugin, pluginName);
    for (SecureStoreData store : stores) {
      secureStores.put(store.getStoreName().toLowerCase(), store);
    }
    return secureStores;
  }

  private Injector createInjectorWithDefaultSecureStore() throws Exception {
    return createInjectorWithCustomSecureStore(DefaultSecureStore.class);
  }

  private Injector createInjectorWithCustomSecureStore(
      final Class<? extends SecureStore> storeClass) throws Exception {
    List<Module> m = Lists.newArrayList();
    m.add(new InitModule(true, true));
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
