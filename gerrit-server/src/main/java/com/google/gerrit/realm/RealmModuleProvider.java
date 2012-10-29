// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.realm;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Iterables.tryFind;
import static com.google.common.collect.Sets.newHashSet;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.io.Closeables;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.SitePaths;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;

public class RealmModuleProvider implements Provider<RealmModule> {
  public static final String REALM_NAME = "Gerrit-RealmName";
  public static final String REALM_MODULE = "Gerrit-RealmModule";

  private final File sitePath;
  private final String authType;

  @Inject
  public RealmModuleProvider(final AuthConfig authConfig,
      final SitePaths sitePaths) {
    this.sitePath = sitePaths.plugins_dir;
    this.authType = authConfig.getAuthType().name();
  }

  @Override
  public RealmModule get() {
    Optional<RealmEntry> realmEntry = getRealmJar();
    if (!realmEntry.isPresent()) {
      return null;
    }
    return getRealmModuleInstance(realmEntry.get());
  }

  private Optional<RealmEntry> getRealmJar() {
    File[] jars = sitePath.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return name.endsWith(".jar");
      }
    });
    Iterable<RealmEntry> realmEntrys =
        transform(newHashSet(jars), new Function<File, RealmEntry>() {
          @Override
          @Nullable
          public RealmEntry apply(@Nullable File input) {
            try {
              return new RealmEntry(input.toURI().toURL(), new JarFile(input));
            } catch (IOException e) {
              return null;
            }
          }
        });
    return tryFind(realmEntrys, new Predicate<RealmEntry>() {
      @Override
      public boolean apply(@Nullable RealmEntry input) {
        String realmName;
        try {
          realmName =
              input.jar.getManifest().getMainAttributes().getValue(REALM_NAME);
        } catch (IOException e) {
          return false;
        }
        if (authType.equalsIgnoreCase(realmName)) {
          return true;
        } else {
          Closeables.closeQuietly(input.jar);
          return false;
        }
      }
    });
  }

  private RealmModule getRealmModuleInstance(RealmEntry realmEntry) {
    URLClassLoader realmLoader = null;
    try {
      String realmClass = gerRealmModuleClassName(realmEntry.jar);
      URL[] urls = new URL[] {realmEntry.jarUrl};
      realmLoader =
          new URLClassLoader(urls, RealmModuleProvider.class.getClassLoader());
      Class<RealmModule> realmModuleClass =
          (Class<RealmModule>) realmLoader.loadClass(realmClass);
      return realmModuleClass.newInstance();
    } catch (Exception e) {
      throw new RealmException("Exception occurs during realm initialization.",
          e);
    } finally {
      Closeables.closeQuietly(realmEntry.jar);
      Closeables.closeQuietly(realmLoader);
    }
  }

  private String gerRealmModuleClassName(JarFile realmJar) throws IOException {
    Manifest manifest = realmJar.getManifest();
    String realmClass = manifest.getMainAttributes().getValue(REALM_MODULE);
    if (realmClass == null) {
      String msg =
          String
              .format("Cannot find %s parameter in MANIFEST.MF", REALM_MODULE);
      throw new RealmException(msg);
    }
    return realmClass;
  }

  private static class RealmEntry {
    final URL jarUrl;
    final JarFile jar;

    RealmEntry(URL url, JarFile jarFile) {
      jarUrl = url;
      jar = jarFile;
    }
  }
}
