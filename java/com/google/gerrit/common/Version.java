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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.annotations.GwtIncompatible;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@GwtIncompatible("Unemulated com.google.gerrit.common.Version")
public class Version {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @VisibleForTesting static final String DEV = "(dev)";

  private static final String VERSION;

  public static String getVersion() {
    return VERSION;
  }

  static {
    VERSION = loadVersion();
  }

  private static String loadVersion() {
    try (InputStream in = Version.class.getResourceAsStream("Version")) {
      if (in == null) {
        return DEV;
      }
      try (BufferedReader r = new BufferedReader(new InputStreamReader(in, UTF_8))) {
        String vs = r.readLine();
        if (vs != null && vs.startsWith("v")) {
          vs = vs.substring(1);
        }
        if (vs != null && vs.isEmpty()) {
          vs = null;
        }
        return vs;
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(e.getMessage());
      return "(unknown version)";
    }
  }

  private Version() {}
}
