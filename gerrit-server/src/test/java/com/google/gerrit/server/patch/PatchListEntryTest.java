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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.reviewdb.client.Patch;
import org.junit.Test;

public class PatchListEntryTest {
  @Test
  public void testEmpty1() {
    final String name = "empty-file";
    final PatchListEntry e = PatchListEntry.empty(name);
    assertNull(e.getOldName());
    assertEquals(name, e.getNewName());
    assertSame(Patch.PatchType.UNIFIED, e.getPatchType());
    assertSame(Patch.ChangeType.MODIFIED, e.getChangeType());
    assertTrue(e.getEdits().isEmpty());
  }
}
