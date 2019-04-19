// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.gerrit.reviewdb.client.PatchSet.joinGroups;
import static com.google.gerrit.reviewdb.client.PatchSet.splitGroups;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class PatchSetTest {
  @Test
  public void parseRefNames() {
    assertRef(1, 1, "refs/changes/01/1/1");
    assertRef(1234, 56, "refs/changes/34/1234/56");

    // Not even close.
    assertNotRef(null);
    assertNotRef("");
    assertNotRef("01/1/1");
    assertNotRef("HEAD");
    assertNotRef("refs/tags/v1");

    // Invalid characters.
    assertNotRef("refs/changes/0x/1/1");
    assertNotRef("refs/changes/01/x/1");
    assertNotRef("refs/changes/01/1/x");

    // Truncations.
    assertNotRef("refs/changes/");
    assertNotRef("refs/changes/1");
    assertNotRef("refs/changes/01");
    assertNotRef("refs/changes/01/");
    assertNotRef("refs/changes/01/1/");
    assertNotRef("refs/changes/01/1/1/");
    assertNotRef("refs/changes/01//1/1");

    // Leading zeroes.
    assertNotRef("refs/changes/01/01/1");
    assertNotRef("refs/changes/01/1/01");

    // Mismatched last 2 digits.
    assertNotRef("refs/changes/35/1234/56");

    // Something other than patch set after change.
    assertNotRef("refs/changes/34/1234/0");
    assertNotRef("refs/changes/34/1234/foo");
    assertNotRef("refs/changes/34/1234|56");
    assertNotRef("refs/changes/34/1234foo");
  }

  @Test
  public void testSplitGroups() {
    assertThat(splitGroups("")).containsExactly("");
    assertThat(splitGroups("abcd")).containsExactly("abcd");
    assertThat(splitGroups("ab,cd")).containsExactly("ab", "cd").inOrder();
    assertThat(splitGroups("ab,")).containsExactly("ab", "").inOrder();
    assertThat(splitGroups(",cd")).containsExactly("", "cd").inOrder();
  }

  @Test
  public void testJoinGroups() {
    assertThat(joinGroups(ImmutableList.of(""))).isEqualTo("");
    assertThat(joinGroups(ImmutableList.of("abcd"))).isEqualTo("abcd");
    assertThat(joinGroups(ImmutableList.of("ab", "cd"))).isEqualTo("ab,cd");
    assertThat(joinGroups(ImmutableList.of("ab", ""))).isEqualTo("ab,");
    assertThat(joinGroups(ImmutableList.of("", "cd"))).isEqualTo(",cd");
  }

  @Test
  public void toRefName() {
    assertThat(PatchSet.id(Change.id(1), 23).toRefName()).isEqualTo("refs/changes/01/1/23");
    assertThat(PatchSet.id(Change.id(1234), 5).toRefName()).isEqualTo("refs/changes/34/1234/5");
  }

  @Test
  public void parseId() {
    assertThat(PatchSet.Id.parse("1,2")).isEqualTo(PatchSet.id(Change.id(1), 2));
    assertThat(PatchSet.Id.parse("01,02")).isEqualTo(PatchSet.id(Change.id(1), 2));
    assertInvalidId(null);
    assertInvalidId("");
    assertInvalidId("1");
    assertInvalidId("1,foo.txt");
    assertInvalidId("foo.txt,1");

    String hexComma = "%" + String.format("%02x", (int) ',');
    assertInvalidId("1" + hexComma + "2");
  }

  @Test
  public void idToString() {
    assertThat(PatchSet.id(Change.id(2), 3).toString()).isEqualTo("2,3");
  }

  private static void assertRef(int changeId, int psId, String refName) {
    assertThat(PatchSet.isChangeRef(refName)).isTrue();
    assertThat(PatchSet.Id.fromRef(refName)).isEqualTo(PatchSet.id(Change.id(changeId), psId));
  }

  private static void assertNotRef(String refName) {
    assertThat(PatchSet.isChangeRef(refName)).isFalse();
    assertThat(PatchSet.Id.fromRef(refName)).isNull();
  }

  private static void assertInvalidId(String str) {
    try {
      PatchSet.Id.parse(str);
      assert_().fail("expected RuntimeException");
    } catch (RuntimeException e) {
      // Expected.
    }
  }
}
