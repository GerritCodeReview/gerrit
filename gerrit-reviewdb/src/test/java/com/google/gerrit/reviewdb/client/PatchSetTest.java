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
  public void testToRefName() {
    assertThat(new PatchSet.Id(new Change.Id(1), 23).toRefName()).isEqualTo("refs/changes/01/1/23");
    assertThat(new PatchSet.Id(new Change.Id(1234), 5).toRefName())
        .isEqualTo("refs/changes/34/1234/5");
  }

  private static void assertRef(int changeId, int psId, String refName) {
    assertThat(PatchSet.isChangeRef(refName)).isTrue();
    assertThat(PatchSet.Id.fromRef(refName))
        .isEqualTo(new PatchSet.Id(new Change.Id(changeId), psId));
  }

  private static void assertNotRef(String refName) {
    assertThat(PatchSet.isChangeRef(refName)).isFalse();
    assertThat(PatchSet.Id.fromRef(refName)).isNull();
  }
}
