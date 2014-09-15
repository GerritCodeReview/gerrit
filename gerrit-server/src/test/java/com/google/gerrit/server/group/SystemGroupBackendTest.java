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

package com.google.gerrit.server.group;

import static org.junit.Assert.*;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

public class SystemGroupBackendTest {

  private static final AccountGroup.UUID UUID = new AccountGroup.UUID(
      "global:Project-Owners");
  private static final AccountGroup.UUID UNKNOWN_UUID = new AccountGroup.UUID(
      "unknown");

  private GroupBackend classUnderTest;

  @Before
  public void setup() {
    classUnderTest = new SystemGroupBackend();
  }

  @Test
  public void testHandles() throws Exception {
    assertTrue(classUnderTest.handles(UUID));
    assertFalse(classUnderTest.handles(UNKNOWN_UUID));
  }

  @Test
  public void testSuggest() throws Exception {
    assertTrue(classUnderTest.suggest("X", null).isEmpty());
    assertTrue(classUnderTest.suggest("Project-Owners", null).isEmpty());
    assertCorrectRef("Project Owners");
    assertCorrectRef("p");
    assertCorrectRef("P");
  }

  @Test
  public void testGetRef() {
    assertRefOK(SystemGroupBackend.getGroup(SystemGroupBackend.PROJECT_OWNERS));
  }

  private void assertCorrectRef(String prefix) {
    Collection<GroupReference> suggestion =
        classUnderTest.suggest(prefix, null);
    assertEquals(1, suggestion.size());
    GroupReference ref = suggestion.iterator().next();
    assertRefOK(ref);
  }

  private void assertRefOK(GroupReference ref) {
    assertEquals(UUID, ref.getUUID());
    assertEquals("Project Owners", ref.getName());
  }

  @Test
  public void testGetDescription() throws Exception {
    assertNull(classUnderTest.get(UNKNOWN_UUID));
    GroupDescription.Basic result = classUnderTest.get(UUID);
    assertEquals("Project Owners", result.getName());
    assertEquals(UUID, result.getGroupUUID());
  }

}
