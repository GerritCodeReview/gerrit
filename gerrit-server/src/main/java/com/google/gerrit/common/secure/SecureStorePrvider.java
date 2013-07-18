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

package com.google.gerrit.common.secure;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;
import com.google.gerrit.common.secure.SecureStoreLoadUtil.Metadata;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
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
public class SecureStorePrvider implements Provider<SecureStore> {
  private final Config config;
  private final SitePaths paths;
  private final Injector injector;
  private final DefaultSecureStore defaultSecureStore;

  @Inject
  SecureStorePrvider(
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
    Set<SecureStoreLoadUtil.Metadata> stores = getSecureStores();
    for (SecureStoreLoadUtil.Metadata store : stores) {
      if (store.storeName.equals(secureName)) {
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

  private Set<Metadata> getSecureStores() {
    File[] plugins = paths.plugins_dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return name.endsWith(".jar");
      }
    });
    Set<SecureStoreLoadUtil.Metadata> result = Sets.newHashSet();
    try {
      for (File plugin : plugins) {
        result.addAll(SecureStoreLoadUtil.discover(plugin));
      };
    } catch (IOException e) {
      throw new SecureStoreException(
          "Exception occur while discovering list of available secure stores",
          e);
    }
    return result;
  }
}
