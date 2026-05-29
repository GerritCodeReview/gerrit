// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.common.collect.Sets;
import com.google.gerrit.launcher.GerritLauncher.GerritClassLoader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/** Provides util methods for dynamic loading jars */
public final class JarUtil {
  public static void loadJars(Collection<Path> jars) {
    if (jars.isEmpty()) {
      return;
    }

    ClassLoader cl = JarUtil.class.getClassLoader();
    if (!(cl instanceof GerritClassLoader)) {
      throw noAddURL("Not loaded by GerritClassLoader", null);
    }

    @SuppressWarnings("resource") // Leave open so classes can be loaded.
    GerritClassLoader gerritClassLoader = (GerritClassLoader) cl;

    Set<URL> have = Sets.newHashSet(Arrays.asList(gerritClassLoader.getURLs()));
    for (Path path : jars) {
      try {
        URL url = path.toUri().toURL();
        if (have.add(url)) {
          gerritClassLoader.addURL(url);
        }
      } catch (MalformedURLException | IllegalArgumentException e) {
        throw noAddURL("addURL " + path + " failed", e);
      }
    }
  }

  public static void loadJars(Path jar) {
    loadJars(Collections.singleton(jar));
  }

  private static UnsupportedOperationException noAddURL(String m, Throwable why) {
    String prefix = "Cannot extend classpath: ";
    return new UnsupportedOperationException(prefix + m, why);
  }

  private JarUtil() {}
}
