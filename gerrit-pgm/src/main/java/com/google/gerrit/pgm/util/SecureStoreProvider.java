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

package com.google.gerrit.pgm.util;

import com.google.common.base.Strings;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SecureStoreProvider implements Provider<SecureStore> {
  private static final Logger log = LoggerFactory
      .getLogger(SecureStoreProvider.class);

  private final File libdir;
  private final Injector injector;
  private final String secureStoreClassName;

  @Inject
  SecureStoreProvider(
      Injector injector,
      SitePaths sitePaths,
      @SecureStoreClassName String secureStoreClassName) {
    this.injector = injector;
    this.libdir = sitePaths.lib_dir;
    this.secureStoreClassName = secureStoreClassName;
  }

  @Override
  public SecureStore get() {
    return injector.getInstance(getSecureStoreImpl());
  }

  @SuppressWarnings("unchecked")
  private Class<? extends SecureStore> getSecureStoreImpl() {
    if (Strings.isNullOrEmpty(secureStoreClassName)) {
      return DefaultSecureStore.class;
    }

    SiteLibraryLoaderUtil.loadSiteLib(libdir);
    try {
      return (Class<? extends SecureStore>) Class.forName(secureStoreClassName);
    } catch (ClassNotFoundException e) {
      String msg =
          String.format("Cannot load secure store class: %s",
              secureStoreClassName);
      log.error(msg, e);
      throw new RuntimeException(msg, e);
    }
  }
}
