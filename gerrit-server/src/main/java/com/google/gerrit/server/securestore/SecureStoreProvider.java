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
import com.google.gerrit.common.SiteLibraryLoaderUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class SecureStoreProvider implements Provider<SecureStore> {
  private static final Logger log = LoggerFactory
      .getLogger(SecureStoreProvider.class);

  private final File libdir;
  private final Injector injector;

  protected String secureStoreClassName;

  private SecureStore instance;

  @Inject
  protected SecureStoreProvider(
      Injector injector,
      SitePaths sitePaths) {
    FileBasedConfig cfg =
        new FileBasedConfig(sitePaths.gerrit_config, FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Cannot read gerrit.config file", e);
    }
    this.injector = injector;
    this.libdir = sitePaths.lib_dir;
    this.secureStoreClassName =
        cfg.getString("gerrit", null, "secureStoreClass");
  }

  @Override
  public SecureStore get() {
    if (instance == null) {
      instance = injector.getInstance(getSecureStoreImpl());
    }
    return instance;
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
