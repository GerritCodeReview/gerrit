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

package com.google.gerrit.server.patch;

import com.google.gerrit.reviewdb.client.Patch;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class PatchListTest {
  @Test
  public void fileOrder() {
    String[] names = {
        "zzz",
        "def/g",
        "/!xxx",
        "abc",
        Patch.MERGE_LIST,
        "qrx",
        Patch.COMMIT_MSG,
    };
    String[] want = {
        Patch.COMMIT_MSG,
        Patch.MERGE_LIST,
        "/!xxx",
        "abc",
        "def/g",
        "qrx",
        "zzz",
    };

    Arrays.sort(names, 0, names.length, new Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        return PatchList.compareNewNames(o1, o2);
      }
    });
    assertThat(names).isEqualTo(want);
  }

}
