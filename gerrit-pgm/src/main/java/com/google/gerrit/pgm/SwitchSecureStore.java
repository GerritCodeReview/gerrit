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
import com.google.common.collect.Iterables;
import com.google.gerrit.common.IoUtil;
import com.google.gerrit.common.SiteLibraryLoaderUtil;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.plugins.JarScanner;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStore.EntryKey;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchSecureStore extends SiteProgram {
  private static String getSecureStoreClassFromGerritConfig(SitePaths sitePaths) {
    FileBasedConfig cfg = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Cannot read gerrit.config file", e);
    }
    return cfg.getString("gerrit", null, "secureStoreClass");
  }

  private static final Logger log = LoggerFactory.getLogger(SwitchSecureStore.class);

  @Option(
    name = "--new-secure-store-lib",
    usage = "Path to new SecureStore implementation",
    required = true
  )
  private String newSecureStoreLib;

  @Override
  public int run() throws Exception {
    SitePaths sitePaths = new SitePaths(getSitePath());
    Path newSecureStorePath = Paths.get(newSecureStoreLib);
    if (!Files.exists(newSecureStorePath)) {
      log.error(String.format("File %s doesn't exist", newSecureStorePath.toAbsolutePath()));
      return -1;
    }

    String newSecureStore = getNewSecureStoreClassName(newSecureStorePath);
    String currentSecureStoreName = getCurrentSecureStoreClassName(sitePaths);

    if (currentSecureStoreName.equals(newSecureStore)) {
      log.error(
          "Old and new SecureStore implementation names "
              + "are the same. Migration will not work");
      return -1;
    }

    IoUtil.loadJARs(newSecureStorePath);
    SiteLibraryLoaderUtil.loadSiteLib(sitePaths.lib_dir);

    log.info(
        "Current secureStoreClass property ({}) will be replaced with {}",
        currentSecureStoreName,
        newSecureStore);
    Injector dbInjector = createDbInjector(SINGLE_USER);
    SecureStore currentStore = getSecureStore(currentSecureStoreName, dbInjector);
    SecureStore newStore = getSecureStore(newSecureStore, dbInjector);

    migrateProperties(currentStore, newStore);

    removeOldLib(sitePaths, currentSecureStoreName);
    copyNewLib(sitePaths, newSecureStorePath);

    updateGerritConfig(sitePaths, newSecureStore);

    return 0;
  }

  private void migrateProperties(SecureStore currentStore, SecureStore newStore) {
    log.info("Migrate entries");
    for (EntryKey key : currentStore.list()) {
      String[] value = currentStore.getList(key.section, key.subsection, key.name);
      if (value != null) {
        newStore.setList(key.section, key.subsection, key.name, Arrays.asList(value));
      } else {
        String msg = String.format("Cannot migrate entry for %s", key.section);
        if (key.subsection != null) {
          msg = msg + String.format(".%s", key.subsection);
        }
        msg = msg + String.format(".%s", key.name);
        throw new RuntimeException(msg);
      }
    }
  }

  private void removeOldLib(SitePaths sitePaths, String currentSecureStoreName) throws IOException {
    Path oldSecureStore = findJarWithSecureStore(sitePaths, currentSecureStoreName);
    if (oldSecureStore != null) {
      log.info("Removing old SecureStore ({}) from lib/ directory", oldSecureStore.getFileName());
      try {
        Files.delete(oldSecureStore);
      } catch (IOException e) {
        log.error("Cannot remove {}", oldSecureStore.toAbsolutePath(), e);
      }
    } else {
      log.info(
          "Cannot find jar with old SecureStore ({}) in lib/ directory", currentSecureStoreName);
    }
  }

  private void copyNewLib(SitePaths sitePaths, Path newSecureStorePath) throws IOException {
    log.info("Copy new SecureStore ({}) into lib/ directory", newSecureStorePath.getFileName());
    Files.copy(newSecureStorePath, sitePaths.lib_dir.resolve(newSecureStorePath.getFileName()));
  }

  private void updateGerritConfig(SitePaths sitePaths, String newSecureStore)
      throws IOException, ConfigInvalidException {
    log.info("Set gerrit.secureStoreClass property of gerrit.config to {}", newSecureStore);
    FileBasedConfig config = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.DETECTED);
    config.load();
    config.setString("gerrit", null, "secureStoreClass", newSecureStore);
    config.save();
  }

  private String getNewSecureStoreClassName(Path secureStore) throws IOException {
    JarScanner scanner = new JarScanner(secureStore);
    List<String> newSecureStores = scanner.findSubClassesOf(SecureStore.class);
    if (newSecureStores.isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Cannot find implementation of SecureStore interface in %s",
              secureStore.toAbsolutePath()));
    }
    if (newSecureStores.size() > 1) {
      throw new RuntimeException(
          String.format(
              "Found too many implementations of SecureStore:\n%s\nin %s",
              Joiner.on("\n").join(newSecureStores), secureStore.toAbsolutePath()));
    }
    return Iterables.getOnlyElement(newSecureStores);
  }

  private String getCurrentSecureStoreClassName(SitePaths sitePaths) {
    String current = getSecureStoreClassFromGerritConfig(sitePaths);
    if (!Strings.isNullOrEmpty(current)) {
      return current;
    }
    return DefaultSecureStore.class.getName();
  }

  private SecureStore getSecureStore(String className, Injector injector) {
    try {
      @SuppressWarnings("unchecked")
      Class<? extends SecureStore> clazz = (Class<? extends SecureStore>) Class.forName(className);
      return injector.getInstance(clazz);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(
          String.format("Cannot load SecureStore implementation: %s", className), e);
    }
  }

  private Path findJarWithSecureStore(SitePaths sitePaths, String secureStoreClass)
      throws IOException {
    List<Path> jars = SiteLibraryLoaderUtil.listJars(sitePaths.lib_dir);
    String secureStoreClassPath = secureStoreClass.replace('.', '/') + ".class";
    for (Path jar : jars) {
      try (JarFile jarFile = new JarFile(jar.toFile())) {
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
