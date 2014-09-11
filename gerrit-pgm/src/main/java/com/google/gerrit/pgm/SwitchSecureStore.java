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

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gerrit.pgm.util.IoUtil;
import com.google.gerrit.pgm.util.SecureStoreProvider;
import com.google.gerrit.pgm.util.SiteLibraryLoaderUtil;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.InvalidPluginException;
import com.google.gerrit.server.plugins.JarScanner;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStore.EntryKey;
import com.google.inject.Injector;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class SwitchSecureStore extends SiteProgram {
  private static final Logger log = LoggerFactory
      .getLogger(SwitchSecureStore.class);

  @Option(name = "--new-secure-store-lib", usage = "Path to new SecureStore implementation", required = true)
  private String newSecureStoreLib;

  @Override
  public int run() throws Exception {
    SitePaths sitePaths = new SitePaths(getSitePath());
    File newSecureStoreFile = new File(newSecureStoreLib);
    if (!newSecureStoreFile.exists()) {
      throw new RuntimeException(String.format("File %s doesn't exists",
          newSecureStoreFile.getAbsolutePath()));
    }

    String newSecureStore = getNewSecureStore(newSecureStoreFile);
    String currentSecureStoreName = getCurrentSecureStoreName(sitePaths);

    if (currentSecureStoreName.equals(newSecureStore)) {
      log.error("Old and new SecureStore implementation names are the same. Migration will not work");
      return -1;
    }

    IoUtil.loadJARs(newSecureStoreFile);
    SiteLibraryLoaderUtil.loadSiteLib(sitePaths.lib_dir);

    Injector dbInjector = createDbInjector(SINGLE_USER);
    SecureStore currentStore =
        getSecureStore(currentSecureStoreName, dbInjector);
    SecureStore newStore = getSecureStore(newSecureStore, dbInjector);

    migrateProperties(currentStore, newStore);

    removeOldLib(sitePaths, currentSecureStoreName);
    copyNewLib(sitePaths, newSecureStoreFile);

    updateGerritConfig(sitePaths, newSecureStore);

    return 0;
  }

  private void migrateProperties(SecureStore currentStore, SecureStore newStore) {
    log.info("Migrate entries");
    for (EntryKey key : currentStore.list()) {
      String[] value =
          currentStore.getList(key.getSection(), key.getSubsection(),
              key.getName());
      if (value != null) {
        newStore.setList(key.getSection(), key.getSubsection(), key.getName(),
            Arrays.asList(value));
      } else {
        String msg =
            String.format("Cannot migrate entry for ", key.getSection());
        if (key.getSubsection() != null) {
          msg = msg + String.format("[%s]", key.getSubsection());
        }
        msg = msg + String.format(".%s", key.getName());
        throw new RuntimeException(msg);
      }
    }
  }

  private void removeOldLib(SitePaths sitePaths, String currentSecureStoreName) {
    File oldSecureStore =
        findJarWithSecureStore(sitePaths, currentSecureStoreName);
    if (oldSecureStore != null) {
      log.info("Removing old SecureStore ({}) from lib/ directory",
          oldSecureStore.getName());
      if (!oldSecureStore.delete()) {
        log.error("Cannot remove {}", oldSecureStore.getAbsolutePath());
      }
    } else {
      log.info("Cannot find jar with old SecureStore ({}) in lib/ directory",
          currentSecureStoreName);
    }
  }

  private void copyNewLib(SitePaths sitePaths, File newSecureStoreFile)
      throws IOException {
    log.info("Copy new SecureStore ({}) into lib/ directory",
        newSecureStoreFile.getName());
    Files.copy(newSecureStoreFile, new File(sitePaths.lib_dir,
        newSecureStoreFile.getName()));
  }

  private void updateGerritConfig(SitePaths sitePaths, String newSecureStore)
      throws IOException, ConfigInvalidException {
    log.info("Set gerrit.secureStoreClass property of gerrit.config to {}",
        newSecureStore);
    FileBasedConfig config =
        new FileBasedConfig(sitePaths.gerrit_config, FS.DETECTED);
    config.load();
    config.setString("gerrit", null, "secureStoreClass", newSecureStore);
    config.save();
  }

  private String getNewSecureStore(File secureStore)
      throws InvalidPluginException {
    JarScanner scanner = new JarScanner(secureStore);
    List<String> newSecureStores =
        scanner.findChildOf(SecureStore.class);
    if (newSecureStores.isEmpty()) {
      throw new RuntimeException(String.format(
          "Cannot find implementation of SecureStore interface in %s",
          secureStore.getAbsolutePath()));
    }
    if (newSecureStores.size() > 1) {
      throw new RuntimeException(String.format(
          "Found to many implementations of SecureStore:\n%s\nin %s", Joiner
              .on("\n").join(newSecureStores), secureStore.getAbsolutePath()));
    }
    return newSecureStores.get(0);
  }

  private String getCurrentSecureStoreName(SitePaths sitePaths) {
    String current =
        SecureStoreProvider.getSecureStoreClassFromGerritConfig(sitePaths);
    if (!Strings.isNullOrEmpty(current)) {
      return current;
    }
    return DefaultSecureStore.class.getName();
  }

  private SecureStore getSecureStore(String className, Injector injector) {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends SecureStore> clazz =
          (Class<? extends SecureStore>) Class.forName(className);
      return injector.getInstance(clazz);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          String.format("Cannot load SecureStore implementation: %s"), e);
    }
  }

  private File findJarWithSecureStore(SitePaths sitePaths,
      String secureStoreClass) {
    File[] jars = SiteLibraryLoaderUtil.listJars(sitePaths.lib_dir);
    if (jars == null || jars.length == 0) {
      return null;
    }
    String secureStoreClassPath = secureStoreClass.replace('.', '/') + ".class";
    for (File jar : jars) {
      try (JarFile jarFile = new JarFile(jar)) {
        ZipEntry entry = jarFile.getEntry(secureStoreClassPath);
        if (entry != null) {
          return jar;
        }
      } catch (IOException e) {
        log.error(e.getMessage(), e);
      }
    }
    return null;
  }
}
