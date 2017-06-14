// Copyright (C) 2012 The Android Open Source Project
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

/** Approximates memory usage for PatchList in bytes of memory used. */
public class PatchListWeigher implements Weigher<PatchListKey, PatchList> {
  @Override
  public int weigh(PatchListKey key, PatchList value) {
    int size =
        16
            + 4 * 8
            + 2 * 36
            + 8 // Size of PatchListKey, 64 bit JVM
            + 16
            + 3 * 8
            + 3 * 4
            + 20; // Size of PatchList, 64 bit JVM
    for (PatchListEntry e : value.getPatches()) {
      size += e.weigh();
    }
    return size;
  }
}
