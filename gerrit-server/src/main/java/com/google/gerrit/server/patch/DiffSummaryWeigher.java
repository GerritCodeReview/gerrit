// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.common.cache.Weigher;

/** Computes memory usage for {@link DiffSummary} in bytes of memory used. */
public class DiffSummaryWeigher implements Weigher<DiffSummaryKey, DiffSummary> {

  @Override
  public int weigh(DiffSummaryKey key, DiffSummary value) {
    int size =
        16
            + 4 * 8
            + 2 * 36 // Size of DiffSummaryKey, 64 bit JVM
            + 16
            + 8 // Size of DiffSummary
            + 16
            + 8; // String[]
    for (String p : value.getPaths()) {
      size +=
          16
              + 8
              + 4 * 4 // String
              + 16
              + 8
              + p.length() * 2; // char[]
    }
    return size;
  }
}
