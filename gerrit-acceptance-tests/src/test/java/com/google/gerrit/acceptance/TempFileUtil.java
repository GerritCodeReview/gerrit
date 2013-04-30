// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class TempFileUtil {
  public static File createTempDirectory() throws IOException {
    File tmp = File.createTempFile("site_", "", tmp());
    if (!tmp.delete() || !tmp.mkdir()) {
      throw new IOException("Cannot create " + tmp.getPath());
    }
    return tmp;
  }

  public static void recursivelyDelete(File dir) throws IOException {
    if (!dir.getPath().equals(dir.getCanonicalPath())) {
      // Directory symlink reaching outside of temporary space.
      throw new IOException("Refusing to clear symlink " + dir.getPath());
    }
    File[] contents = dir.listFiles();
    if (contents != null) {
      for (File d : contents) {
        if (d.isDirectory()) {
          recursivelyDelete(d);
        } else {
          d.delete();
        }
      }
      dir.delete();
    }
  }

  private static File tmp() {
    URL url = TempFileUtil.class.getResource("TempFileUtil.class");
    if (url == null) {
      fail("cannot locate build directory");
    }

    File clazz;
    if ("file".equals(url.getProtocol())) {
      clazz = new File(url.getPath());
    } else if ("jar".equals(url.getProtocol())) {
      String path = url.getPath();
      int bang = path.indexOf('!');
      assertTrue(path + " starts with file:", path.startsWith("file:"));
      assertTrue(path + " contains !", 0 < bang);
      try {
        clazz = new File(new URI(path.substring(0, bang)));
      } catch (URISyntaxException e) {
        throw new RuntimeException("cannot locate build directory", e);
      }
    } else {
      fail("cannot locate build directory");
      return null;
    }

    File dir = clazz.getParentFile();
    while (dir != null) {
      String n = dir.getName();
      if ("buck-out".equals(n) || "target".equals(n)) {
        File tmp = new File(dir, "tmp");
        tmp.mkdir();
        return tmp;
      }
      dir = dir.getParentFile();
    }
    fail("cannot locate tmp for test");
    return null;
  }

  private TempFileUtil() {
  }
}
