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

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.SiteLibraryLoaderUtil;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SecureStoreProvider implements Provider<SecureStore> {
  private static final Logger log = LoggerFactory.getLogger(SecureStoreProvider.class);

  private final Path libdir;
  private final Injector injector;
  private final String className;

  @Inject
  protected SecureStoreProvider(
      Injector injector, SitePaths sitePaths, @Nullable @SecureStoreClassName String className) {
    this.injector = injector;
    this.libdir = sitePaths.lib_dir;
    this.className = className;
  }

  @Override
  public synchronized SecureStore get() {
    return injector.getInstance(getSecureStoreImpl());
  }

  @SuppressWarnings("unchecked")
  private Class<? extends SecureStore> getSecureStoreImpl() {
    if (Strings.isNullOrEmpty(className)) {
      return DefaultSecureStore.class;
    }

    SiteLibraryLoaderUtil.loadSiteLib(libdir);
    try {
      return (Class<? extends SecureStore>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      String msg = String.format("Cannot load secure store class: %s", className);
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }
}
