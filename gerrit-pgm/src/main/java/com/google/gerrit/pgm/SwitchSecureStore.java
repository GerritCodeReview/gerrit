// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.gerrit.pgm.util.IoUtil;
import com.google.gerrit.pgm.util.SecureStoreClassNameProvider;
import com.google.gerrit.pgm.util.SiteLibraryLoaderUtil;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.JarScanner;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStore.EntryKey;
import com.google.inject.Injector;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class SwitchSecureStore extends SiteProgram {
  @Option(name = "--new-secure-store-lib", usage = "Path to new SecureStore implementation", required = true)
  private String newSecureStoreLib;

  @Override
  public int run() throws Exception {
    SitePaths sitePaths = new SitePaths(getSitePath());
    File newSecureStore = new File(newSecureStoreLib);
    if (!newSecureStore.exists()) {
      // throw
    }
    JarScanner scanner = new JarScanner(newSecureStore);
    List<String> newSecureStores =
        scanner.findImplementationsOf(SecureStore.class);
    if (newSecureStores.isEmpty()) {
      // throw
    }
    if (newSecureStores.size() > 1) {
      // throw
    }
    IoUtil.loadJARs(newSecureStore);
    SiteLibraryLoaderUtil.loadSiteLib(sitePaths.lib_dir);

    Injector dbInjector = createDbInjector(SINGLE_USER);

    String currentSecureStoreName = getCurrentSecureStoreName(dbInjector);
    SecureStore currentStore =
        getSecureStore(currentSecureStoreName, dbInjector);
    SecureStore newStore = getSecureStore(newSecureStores.get(0), dbInjector);

    for (EntryKey key : currentStore.list()) {
      String value =
          currentStore
              .get(key.getSection(), key.getSubsection(), key.getName());
      if (value != null) {
        newStore.set(key.getSection(), key.getSubsection(), key.getName(),
            value);
      } else {
        String[] listValue =
            currentStore.getList(key.getSection(), key.getSubsection(),
                key.getName());
        if (listValue != null) {
          newStore.setList(key.getSection(), key.getSubsection(),
              key.getName(), Arrays.asList(listValue));
        } else {
          // throw
        }
      }
    }

    return 0;
  }

  private String getCurrentSecureStoreName(Injector injector) {
    SecureStoreClassNameProvider secureStoreNameProvider =
        injector.getInstance(SecureStoreClassNameProvider.class);
    return secureStoreNameProvider.get();
  }

  private SecureStore getSecureStore(String className, Injector injector) {
    try {
      Class<? extends SecureStore> clazz =
          (Class<? extends SecureStore>) Class.forName(className);
      return injector.getInstance(clazz);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          String.format("Cannot load SecureStore implementation: %s"), e);
    }
  }
}
