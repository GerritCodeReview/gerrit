// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the global server instance for test does not retain certain types of objects. If we
 * did, that would almost certainly be an unintended memory leak.
 */
public class MemoryLeakChecker {

  // Should be the full name as it appears in jmap output.
  private static List<String> forbidden =
      ImmutableList.of(
          // Repositories should be closed.
          "org.eclipse.jgit.lib.Repository",

          // If we serve no traffic, we should have no request contexts.
          "com.google.gerrit.httpd.HttpRequestContext");

  /**
   * return forbidden objects found in the heap profile. This is somewhat expensive, ~500ms per call
   */
  public static List<String> getForbidden() {
    long start = System.nanoTime();
    Process process;
    try {
      String pid = new File("/proc/self").getCanonicalFile().getName();

      process = new ProcessBuilder("jmap", "-histo:live", pid).start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    List<String> result = new ArrayList<>();

    try (java.util.Scanner s = new java.util.Scanner(process.getInputStream())) {
      s.useDelimiter("\n");
      while (s.hasNext()) {
        String line = s.next();
        for (String f : forbidden) {
          if (line.endsWith(f)) {
            result.add(line);
          }
        }
      }
    }

    try {
      process.waitFor();
    } catch (InterruptedException e) {
      // whatever.
    }

    System.err.println("took ms: " + (System.nanoTime() - start) / 1000000.0);
    return result;
  }
}
