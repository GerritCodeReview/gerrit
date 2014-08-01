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
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ChangeTest {
  @Test
  public void parseInvalidRefNames() {
    assertNotRef(null);
    assertNotRef("");
    assertNotRef("01/1/1");
    assertNotRef("HEAD");
    assertNotRef("refs/tags/v1");
  }

  @Test
  public void parsePatchSetRefNames() {
    assertRef(1, "refs/changes/01/1/1");
    assertRef(1234, "refs/changes/34/1234/56");

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
  public void parseChangeMetaRefNames() {
    assertRef(1, "refs/changes/01/1/meta");
    assertRef(1234, "refs/changes/34/1234/meta");

    assertNotRef("refs/changes/01/1/met");
    assertNotRef("refs/changes/01/1/META");
    assertNotRef("refs/changes/01/1/1/meta");
  }

  private static void assertRef(int changeId, String refName) {
    assertEquals(new Change.Id(changeId), Change.Id.fromRef(refName));
  }

  private static void assertNotRef(String refName) {
    assertNull(Change.Id.fromRef(refName));
  }
}
