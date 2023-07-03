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

import static com.google.gerrit.launcher.GerritLauncher.GerritClassLoader;

import com.google.common.collect.Sets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public final class IoUtil {
  public static void copyWithThread(InputStream src, OutputStream dst) {
    new Thread("IoUtil-Copy") {
      // We cannot propagate the exception since this code is running in a background thread.
      // Printing the stacktrace is the best we can do. Hence ignoring the exception after printing
      // the stacktrace is OK and it's fine to suppress the warning for the CatchAndPrintStackTrace
      // bug pattern here.
      @SuppressWarnings("CatchAndPrintStackTrace")
      @Override
      public void run() {
        try {
          copyIo();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }

      private void copyIo() throws IOException {
        try {
          final byte[] buf = new byte[256];
          int n;
          while (0 < (n = src.read(buf))) {
            dst.write(buf, 0, n);
          }
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

  public static void loadJARs(Collection<Path> jars) {
    if (jars.isEmpty()) {
      return;
    }

    ClassLoader cl = IoUtil.class.getClassLoader();
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

  public static void loadJARs(Path jar) {
    loadJARs(Collections.singleton(jar));
  }

  private static UnsupportedOperationException noAddURL(String m, Throwable why) {
    String prefix = "Cannot extend classpath: ";
    return new UnsupportedOperationException(prefix + m, why);
  }

  private IoUtil() {}
}
