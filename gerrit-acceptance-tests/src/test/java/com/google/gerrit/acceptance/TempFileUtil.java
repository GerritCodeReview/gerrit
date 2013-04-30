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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TempFileUtil {
  public static File createTempDirectory() throws IOException {
    String dt = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    File tmp = File.createTempFile("gerrit_test_" + dt + "_", "_site");
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

  private TempFileUtil() {
  }
}
