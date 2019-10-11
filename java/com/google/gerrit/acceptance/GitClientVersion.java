// Copyright (C) 2019 The Android Open Source Project
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

import static java.util.stream.Collectors.joining;

import java.util.stream.IntStream;

/** Class to parse and represent version of git-core client */
public class GitClientVersion implements Comparable<GitClientVersion> {
  private final int v[];

  /**
   * Constructor to represent instance for minimum supported git-core version
   *
   * @param parts version passed as single digits
   */
  public GitClientVersion(int... parts) {
    this.v = parts;
  }

  /**
   * Parse the git-core version as returned by git version command
   *
   * @param version String returned by git version command
   */
  public GitClientVersion(String version) {
    // "git version x.y.z", at Google "git version x.y.z.gXXXXXXXXXX-goog"
    String parts[] = version.split(" ")[2].split("\\.");
    int numParts = Math.min(parts.length, 3); // ignore Google-specific part of the version
    v = new int[numParts];
    for (int i = 0; i < numParts; i++) {
      v[i] = Integer.valueOf(parts[i]);
    }
  }

  @Override
  public int compareTo(GitClientVersion o) {
    int m = Math.max(v.length, o.v.length);
    for (int i = 0; i < m; i++) {
      int l = i < v.length ? v[i] : 0;
      int r = i < o.v.length ? o.v[i] : 0;
      if (l != r) {
        return l < r ? -1 : 1;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return IntStream.of(v).mapToObj(String::valueOf).collect(joining("."));
  }
}
