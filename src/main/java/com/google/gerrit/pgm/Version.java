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

package com.google.gerrit.pgm;

import com.google.gerrit.client.GerritVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/** Display the version of Gerrit. */
public class Version {
  private static String version;

  public static synchronized String getVersion() {
    if (version == null) {
      final Properties p = new Properties();
      final InputStream in =
          GerritVersion.class.getResourceAsStream(GerritVersion.class
              .getSimpleName()
              + ".properties");
      if (in == null) {
        return null;
      }
      try {
        try {
          p.load(in);
        } finally {
          in.close();
        }
        version = p.getProperty("version");
      } catch (IOException e) {
        return null;
      }
    }
    return version;
  }

  public static void main(final String[] argv) {
    final String v = getVersion();
    if (v == null) {
      System.err.println("fatal: version unavailable");
      System.exit(1);
    }
    System.out.println("gerrit version " + v);
  }
}
