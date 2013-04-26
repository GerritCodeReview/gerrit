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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Standard {@link LibraryDownloader} instances derived from configuration. */
@Singleton
class Libraries {
  private static final String RESOURCE_FILE =
      "com/google/gerrit/pgm/libraries.config";

  private final Provider<LibraryDownloader> downloadProvider;

  /* final */LibraryDownloader bouncyCastle;
  /* final */LibraryDownloader mysqlDriver;

  @Inject
  Libraries(final Provider<LibraryDownloader> downloadProvider) {
    this.downloadProvider = downloadProvider;

    init();
  }

  private void init() {
    final Config cfg = new Config();
    try {
      cfg.fromText(read(RESOURCE_FILE));
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    } catch (ConfigInvalidException e) {
      throw new RuntimeException(e.getMessage(), e);
    }

    for (final Field f : Libraries.class.getDeclaredFields()) {
      if ((f.getModifiers() & Modifier.STATIC) == 0
          && f.getType() == LibraryDownloader.class) {
        try {
          init(f, cfg);
        } catch (IllegalArgumentException e) {
          throw new IllegalStateException("Cannot initialize " + f.getName());
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Cannot initialize " + f.getName());
        }
      }
    }
  }

  private void init(final Field field, final Config cfg)
      throws IllegalArgumentException, IllegalAccessException {
    final String n = field.getName();
    final LibraryDownloader dl = downloadProvider.get();
    dl.setName(get(cfg, n, "name"));
    dl.setJarUrl(get(cfg, n, "url"));
    dl.setSHA1(get(cfg, n, "sha1"));
    dl.setRemove(get(cfg, n, "remove"));
    field.set(this, dl);
  }

  private static String get(Config cfg, String name, String key) {
    String val = cfg.getString("library", name, key);
    if (val == null || val.isEmpty()) {
      throw new IllegalStateException("Variable library." + name + "." + key
          + " is required within " + RESOURCE_FILE);
    }
    return val;
  }

  private static String read(final String p) throws IOException {
    InputStream in = Libraries.class.getClassLoader().getResourceAsStream(p);
    if (in == null) {
      throw new FileNotFoundException("Cannot load resource " + p);
    }
    final Reader r = new InputStreamReader(in, "UTF-8");
    try {
      final StringBuilder buf = new StringBuilder();
      final char[] tmp = new char[512];
      int n;
      while (0 < (n = r.read(tmp))) {
        buf.append(tmp, 0, n);
      }
      return buf.toString();
    } finally {
      r.close();
    }
  }
}
