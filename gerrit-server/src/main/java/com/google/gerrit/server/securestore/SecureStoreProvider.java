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

package com.google.gerrit.server.securestore;

import static com.google.gerrit.server.plugins.PluginGuiceEnvironment.is;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.annotations.Export;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.JarScanner;
import com.google.gerrit.server.plugins.JarScanner.ExtensionMetaData;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Set;

@Singleton
public class SecureStoreProvider implements Provider<SecureStore> {
  private final Config config;
  private final SitePaths paths;
  private final Injector injector;
  private final DefaultSecureStore defaultSecureStore;

  public static Set<SecureStoreData> findSecureStores(File pluginFile,
      String pluginName) throws InvalidPluginException, IOException,
      ClassNotFoundException {
    ImmutableSet.Builder<SecureStoreData> result =
        new ImmutableSet.Builder<SecureStoreData>();
    Iterable<ExtensionMetaData> exports =
        JarScanner.scan(pluginFile, pluginName, Export.class);
    for (ExtensionMetaData export : exports) {
      String className = export.getClassName();
      Class<?> clazz = Class.forName(className);
      if (is("com.google.gerrit.server.securestore.SecureStore", clazz)) {
        String storeName = export.getAnnotationValue();
        SecureStoreData metadata =
            new SecureStoreData(pluginName, className, pluginFile, storeName);
        result.add(metadata);
      }
    }
    return result.build();
  }

  @Inject
  SecureStoreProvider(
      SitePaths paths,
      Injector injector,
      DefaultSecureStore defaultSecureStore,
      @GerritServerConfig Config config) {
    this.paths = paths;
    this.config = config;
    this.injector = injector;
    this.defaultSecureStore = defaultSecureStore;
  }

  @Override
  public SecureStore get() {
    String secureName = getCurrentSecureStoreName();
    if (DefaultSecureStore.NAME.equals(secureName)) {
      return defaultSecureStore;
    }
    Set<SecureStoreData> stores = getSecureStores();
    for (SecureStoreData store : stores) {
      if (store.getStoreName().equals(secureName)) {
        Class<? extends SecureStore> secureStoreImpl = store.load();
        return injector.getInstance(secureStoreImpl);
      }
    }
    throw new SecureStoreException(String.format(
        "Cannot find secure store with name %s", secureName));
  }

  private String getCurrentSecureStoreName() {
    return Objects.firstNonNull(
        config.getString("gerrit", null, "secureStore"),
        DefaultSecureStore.NAME);
  }

  private Set<SecureStoreData> getSecureStores() {
    File[] plugins = paths.plugins_dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return name.endsWith(".jar");
      }
    });
    Set<SecureStoreData> result = Sets.newHashSet();
    try {
      for (File plugin : plugins) {
        String pluginName = PluginLoader.getPluginName(plugin);
        result.addAll(findSecureStores(plugin, pluginName));
      }
    } catch (ClassNotFoundException e) {
      throwSecureStoreException(e);
    } catch (InvalidPluginException e) {
      throwSecureStoreException(e);
    } catch (IOException e) {
      throwSecureStoreException(e);
    }
    return result;
  }

  private void throwSecureStoreException(Exception e) {
    throw new SecureStoreException(
        "Exception occur while discovering list of available secure stores",
        e);
  }
}
