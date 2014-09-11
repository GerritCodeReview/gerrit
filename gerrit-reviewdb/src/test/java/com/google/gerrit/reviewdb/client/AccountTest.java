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

public class AccountTest {
  @Test
  public void parseRefName() {
    assertRef(1, "refs/users/01/1");
    assertRef(1, "refs/users/01/1-drafts");
    assertRef(1, "refs/users/01/1-drafts/2");
    assertRef(1, "refs/users/01/1/edit/2");

    assertNotRef(null);
    assertNotRef("");

    // Invalid characters.
    assertNotRef("refs/users/01a/1");
    assertNotRef("refs/users/01/a1");

    // Mismatched shard.
    assertNotRef("refs/users/01/23");

    // Shard too short.
    assertNotRef("refs/users/1/1");
  }

  @Test
  public void parseRefNameParts() {
    assertRefPart(1, "01/1");
    assertRefPart(1, "01/1-drafts");
    assertRefPart(1, "01/1-drafts/2");

    assertNotRefPart(null);
    assertNotRefPart("");

    // This method assumes that the common prefix "refs/users/" will be removed.
    assertNotRefPart("refs/users/01/1");

    // Invalid characters.
    assertNotRefPart("01a/1");
    assertNotRefPart("01/a1");

    // Mismatched shard.
    assertNotRefPart("01/23");

    // Shard too short.
    assertNotRefPart("1/1");
  }

  private static void assertRef(int accountId, String refName) {
    assertEquals(new Account.Id(accountId), Account.Id.fromRef(refName));
  }

  private static void assertNotRef(String refName) {
    assertNull(Account.Id.fromRef(refName));
  }

  private static void assertRefPart(int accountId, String refName) {
    assertEquals(new Account.Id(accountId), Account.Id.fromRefPart(refName));
  }

  private static void assertNotRefPart(String refName) {
    assertNull(Account.Id.fromRefPart(refName));
  }
}
