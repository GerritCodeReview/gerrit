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

package com.google.gerrit.pgm.util;

import org.eclipse.jgit.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class IoUtil {
  public static final boolean isWin32() {
    final String osDotName = System.getProperty("os.name");
    return osDotName != null
        && StringUtils.toLowerCase(osDotName).indexOf("windows") != -1;
  }

  public static void copyWithThread(final InputStream src,
      final OutputStream dst) {
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
          }
        }
      }
    }.start();
  }

  private IoUtil() {
  }
}
