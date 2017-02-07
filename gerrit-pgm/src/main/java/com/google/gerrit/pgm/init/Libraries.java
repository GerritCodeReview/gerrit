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

package com.google.gerrit.pgm.init;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.pgm.init.api.LibraryDownload;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Standard {@link LibraryDownloader} instances derived from configuration. */
@Singleton
class Libraries {
  private static final String RESOURCE_FILE = "com/google/gerrit/pgm/init/libraries.config";

  private final Provider<LibraryDownloader> downloadProvider;
  private final List<String> skippedDownloads;
  private final boolean skipAllDownloads;

  /* final */ LibraryDownloader bouncyCastlePGP;
  /* final */ LibraryDownloader bouncyCastleProvider;
  /* final */ LibraryDownloader bouncyCastleSSL;
  /* final */ LibraryDownloader db2Driver;
  /* final */ LibraryDownloader db2DriverLicense;
  /* final */ LibraryDownloader hanaDriver;
  /* final */ LibraryDownloader mysqlDriver;
  /* final */ LibraryDownloader oracleDriver;

  @Inject
  Libraries(
      final Provider<LibraryDownloader> downloadProvider,
      @LibraryDownload List<String> skippedDownloads,
      @LibraryDownload Boolean skipAllDownloads) {
    this.downloadProvider = downloadProvider;
    this.skippedDownloads = skippedDownloads;
    this.skipAllDownloads = skipAllDownloads;
    init();
  }

  private void init() {
    final Config cfg = new Config();
    try {
      cfg.fromText(read(RESOURCE_FILE));
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    for (Field f : Libraries.class.getDeclaredFields()) {
      if ((f.getModifiers() & Modifier.STATIC) == 0 && f.getType() == LibraryDownloader.class) {
        try {
          f.set(this, downloadProvider.get());
        } catch (IllegalArgumentException | IllegalAccessException e) {
          throw new IllegalStateException("Cannot initialize " + f.getName());
        }
      }
    }

    for (Field f : Libraries.class.getDeclaredFields()) {
      if ((f.getModifiers() & Modifier.STATIC) == 0 && f.getType() == LibraryDownloader.class) {
        try {
          init(f, cfg);
        } catch (IllegalArgumentException
            | IllegalAccessException
            | NoSuchFieldException
            | SecurityException e) {
          throw new IllegalStateException("Cannot configure " + f.getName());
        }
      }
    }
  }

  private void init(Field field, Config cfg)
      throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
          SecurityException {
    String n = field.getName();
    LibraryDownloader dl = (LibraryDownloader) field.get(this);
    dl.setName(get(cfg, n, "name"));
    dl.setJarUrl(get(cfg, n, "url"));
    dl.setSHA1(getOptional(cfg, n, "sha1"));
    dl.setRemove(get(cfg, n, "remove"));
    for (String d : cfg.getStringList("library", n, "needs")) {
      dl.addNeeds((LibraryDownloader) getClass().getDeclaredField(d).get(this));
    }
    dl.setSkipDownload(skipAllDownloads || skippedDownloads.contains(n));
  }

  private static String getOptional(Config cfg, String name, String key) {
    return doGet(cfg, name, key, false);
  }

  private static String get(Config cfg, String name, String key) {
    return doGet(cfg, name, key, true);
  }

  private static String doGet(Config cfg, String name, String key, boolean required) {
    String val = cfg.getString("library", name, key);
    if ((val == null || val.isEmpty()) && required) {
      throw new IllegalStateException(
          "Variable library." + name + "." + key + " is required within " + RESOURCE_FILE);
    }
    return val;
  }

  private static String read(final String p) throws IOException {
    try (InputStream in = Libraries.class.getClassLoader().getResourceAsStream(p)) {
      if (in == null) {
        throw new FileNotFoundException("Cannot load resource " + p);
      }
      try (Reader r = new InputStreamReader(in, UTF_8)) {
        final StringBuilder buf = new StringBuilder();
        final char[] tmp = new char[512];
        int n;
        while (0 < (n = r.read(tmp))) {
          buf.append(tmp, 0, n);
        }
        return buf.toString();
      }
    }
  }
}
