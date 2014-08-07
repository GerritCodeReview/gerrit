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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
  }

  private static void assertRef(int changeId, int psId, String refName) {
    assertTrue(PatchSet.isRef(refName));
    assertEquals(new PatchSet.Id(new Change.Id(changeId), psId),
        PatchSet.Id.fromRef(refName));
  }

  private static void assertNotRef(String refName) {
    assertFalse(PatchSet.isRef(refName));
    assertNull(PatchSet.Id.fromRef(refName));
  }
}
