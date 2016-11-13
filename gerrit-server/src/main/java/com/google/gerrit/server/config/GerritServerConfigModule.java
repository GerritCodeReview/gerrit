// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreProvider;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

/** Creates {@link GerritServerConfig}. */
public class GerritServerConfigModule extends AbstractModule {
  public static String getSecureStoreClassName(final Path sitePath) {
    if (sitePath != null) {
      return getSecureStoreFromGerritConfig(sitePath);
    }

    String secureStoreProperty = System.getProperty("gerrit.secure_store_class");
    return nullToDefault(secureStoreProperty);
  }

  private static String getSecureStoreFromGerritConfig(final Path sitePath) {
    AbstractModule m =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Path.class).annotatedWith(SitePath.class).toInstance(sitePath);
            bind(SitePaths.class);
          }
        };
    Injector injector = Guice.createInjector(m);
    SitePaths site = injector.getInstance(SitePaths.class);
    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config.toFile(), FS.DETECTED);
    if (!cfg.getFile().exists()) {
      return DefaultSecureStore.class.getName();
    }

    try {
      cfg.load();
      String className = cfg.getString("gerrit", null, "secureStoreClass");
      return nullToDefault(className);
    } catch (IOException | ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }
  }

  private static String nullToDefault(String className) {
    return className != null ? className : DefaultSecureStore.class.getName();
  }

  @Override
  protected void configure() {
    bind(SitePaths.class);
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);
    bind(Config.class)
        .annotatedWith(GerritServerConfig.class)
        .toProvider(GerritServerConfigProvider.class)
        .in(SINGLETON);
    bind(SecureStore.class).toProvider(SecureStoreProvider.class).in(SINGLETON);
  }
}
