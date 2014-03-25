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

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.collect.Iterables.tryFind;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
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

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;
import java.util.Set;

public class SecureStoreProvider implements Provider<SecureStore> {
  private static final String SECURE_STORE_INTERFACE = SecureStore.class.getName();

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
      Optional<String> implementsSecureStore =
          tryFind(export.getInterfaces(), equalTo(SECURE_STORE_INTERFACE));
      if (implementsSecureStore.isPresent()) {
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
    String secureStore = getCurrentSecureStoreName();
    if (DefaultSecureStore.NAME.equals(secureStore)) {
      return defaultSecureStore;
    }
    int separatorPosition = secureStore.lastIndexOf("/");
    if (separatorPosition < 0) {
      throw new SecureStoreException(String.format(
          "Unsupported secure store format %s."
              + "Proper format is: $plugin_name/$secure_store_name",
          secureStore));
    }
    String storeName = secureStore.substring(separatorPosition + 1);
    String pluginName = secureStore.substring(0, separatorPosition);
    Set<SecureStoreData> stores = getSecureStores(pluginName);
    for (SecureStoreData store : stores) {
      if (store.getStoreName().equals(secureStore)) {
        Class<? extends SecureStore> secureStoreImpl = store.load();
        return new SecureStoreWrapper(injector.getInstance(secureStoreImpl));
      }
    }
    throw new SecureStoreException(String.format(
        "Cannot find secure store with name %s in plugin %s", storeName,
        pluginName));
  }

  private String getCurrentSecureStoreName() {
    return Objects.firstNonNull(
        config.getString("gerrit", null, "secureStore"),
        DefaultSecureStore.NAME);
  }

  private Set<SecureStoreData> getSecureStores(String pluginName) {
    Multimap<String, File> plugins = PluginLoader.prunePlugins(paths.plugins_dir);
    if (!plugins.containsKey(pluginName) || plugins.get(pluginName).isEmpty()) {
      throwCannotFindPluginException(pluginName);
    }
    File plugin = Iterables.getFirst(plugins.get(pluginName), null);
    if (plugin == null) {
      throwCannotFindPluginException(pluginName);
    }
    Set<SecureStoreData> result = Sets.newHashSet();
    try {
      result.addAll(findSecureStores(plugin, pluginName));
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

  private void throwCannotFindPluginException(String pluginName) {
    throw new SecureStoreException(String.format(
        "Cannot find plugin with name: %s", pluginName));
  }

  private static class SecureStoreWrapper implements SecureStore {
    private SecureStore impl;

    SecureStoreWrapper(SecureStore impl) {
      this.impl = impl;
    }

    @Override
    public String get(String section, String subsection, String name) {
      String value = impl.get(section, subsection, name);
      // ensure that property is always encrypted
      impl.set(section, subsection, name, value);
      return value;
    }

    @Override
    public void set(String section, String subsection, String name, String value) {
      impl.set(section, subsection, name, value);
    }

    @Override
    public void unset(String section, String subsection, String name) {
      impl.unset(section, subsection, name);
    }
  }
}
