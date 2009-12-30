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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Version {
  private static final String version;

  public static String getVersion() {
    return version;
  }

  static {
    version = loadVersion();
  }

  private static String loadVersion() {
    InputStream in = Version.class.getResourceAsStream("Version");
    if (in == null) {
      return null;
    }
    try {
      BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
      try {
        String vs = r.readLine();
        if (vs != null && vs.startsWith("v")) {
          vs = vs.substring(1);
        }
        if (vs != null && vs.isEmpty()) {
          vs = null;
        }
        return vs;
      } finally {
        r.close();
      }
    } catch (IOException e) {
      return null;
    }
  }

  private Version() {
  }
}
