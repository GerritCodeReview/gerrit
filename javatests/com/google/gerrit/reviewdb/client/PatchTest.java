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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;

import com.google.gerrit.testing.GerritBaseTests;
import org.junit.Test;

public class PatchTest extends GerritBaseTests {
  @Test
  public void isMagic() {
    assertThat(Patch.isMagic("/COMMIT_MSG")).isTrue();
    assertThat(Patch.isMagic("/MERGE_LIST")).isTrue();

    assertThat(Patch.isMagic("/COMMIT_MSG/")).isFalse();
    assertThat(Patch.isMagic("COMMIT_MSG")).isFalse();
    assertThat(Patch.isMagic("/commit_msg")).isFalse();
  }

  @Test
  public void parseKey() {
    assertThat(Patch.Key.parse("1,2,foo.txt"))
        .isEqualTo(Patch.key(new PatchSet.Id(new Change.Id(1), 2), "foo.txt"));
    assertThat(Patch.Key.parse("01,02,foo.txt"))
        .isEqualTo(Patch.key(new PatchSet.Id(new Change.Id(1), 2), "foo.txt"));
    assertInvalidKey(null);
    assertInvalidKey("");
    assertInvalidKey("1,2");
    assertInvalidKey("1, 2, foo.txt");
    assertInvalidKey("1,foo.txt");
    assertInvalidKey("1,foo.txt,2");
    assertInvalidKey("foo.txt,1,2");

    String hexComma = "%" + String.format("%02x", (int) ',');
    assertInvalidKey("1" + hexComma + "2" + hexComma + "foo.txt");
  }

  private static void assertInvalidKey(String str) {
    try {
      Patch.Key.parse(str);
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
