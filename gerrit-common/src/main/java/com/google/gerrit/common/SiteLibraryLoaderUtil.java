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

package com.google.gerrit.common;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;

public final class SiteLibraryLoaderUtil {

  public static void loadSiteLib(File libdir) {
    File[] jars = listJars(libdir);
    if (jars != null && 0 < jars.length) {
      Arrays.sort(jars, new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
          // Sort by reverse last-modified time so newer JARs are first.
          int cmp = Long.compare(b.lastModified(), a.lastModified());
          if (cmp != 0) {
            return cmp;
          }
          return a.getName().compareTo(b.getName());
        }
      });
      IoUtil.loadJARs(jars);
    }
  }

  public static File[] listJars(File libdir) {
    File[] jars = libdir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File path) {
        String name = path.getName();
        return (name.endsWith(".jar") || name.endsWith(".zip"))
            && path.isFile();
      }
    });
    return jars;
  }

  private SiteLibraryLoaderUtil() {
  }
}
