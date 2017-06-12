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

package com.google.gerrit.common;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

@GwtIncompatible("Unemulated methods in Class and OutputStream")
public final class IoUtil {
  public static void copyWithThread(final InputStream src, final OutputStream dst) {
    new Thread("IoUtil-Copy") {
      @Override
      public void run() {
        try {
          final byte[] buf = new byte[256];
          int n;
          while (0 < (n = src.read(buf))) {
            dst.write(buf, 0, n);
          }
        } catch (IOException e) {
          e.printStackTrace();
        } finally {
          try {
            src.close();
          } catch (IOException e2) {
            // Ignore
          }
        }
      }
    }.start();
  }

  public static void loadJARs(Iterable<Path> jars) {
    ClassLoader cl = IoUtil.class.getClassLoader();
    if (!(cl instanceof URLClassLoader)) {
      throw noAddURL("Not loaded by URLClassLoader", null);
    }

    @SuppressWarnings("resource") // Leave open so classes can be loaded.
    URLClassLoader urlClassLoader = (URLClassLoader) cl;

    Method addURL;
    try {
      addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      addURL.setAccessible(true);
    } catch (SecurityException | NoSuchMethodException e) {
      throw noAddURL("Method addURL not available", e);
    }

    Set<URL> have = Sets.newHashSet(Arrays.asList(urlClassLoader.getURLs()));
    for (Path path : jars) {
      try {
        URL url = path.toUri().toURL();
        if (have.add(url)) {
          addURL.invoke(cl, url);
        }
      } catch (MalformedURLException | IllegalArgumentException | IllegalAccessException e) {
        throw noAddURL("addURL " + path + " failed", e);
      } catch (InvocationTargetException e) {
        throw noAddURL("addURL " + path + " failed", e.getCause());
      }
    }
  }

  public static void loadJARs(Path jar) {
    loadJARs(Collections.singleton(jar));
  }

  private static UnsupportedOperationException noAddURL(String m, Throwable why) {
    String prefix = "Cannot extend classpath: ";
    return new UnsupportedOperationException(prefix + m, why);
  }

  private IoUtil() {}
}
