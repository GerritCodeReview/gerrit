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

import com.google.gerrit.client.reviewdb.Patch;

import junit.framework.TestCase;

public class PatchListEntryTest extends TestCase {
  public void testEmpty1() {
    final String name = "empty-file";
    final PatchListEntry e = PatchListEntry.empty(name);
    assertNull(e.getOldName());
    assertEquals(name, e.getNewName());

    // I'm not sure why JGit is calling this binary, it may be because
    // there are no edits on the file, which is fine.
    //
    assertSame(Patch.PatchType.BINARY, e.getPatchType());
    assertSame(Patch.ChangeType.MODIFIED, e.getChangeType());
    assertNotNull(e.getFileHeader());
    assertTrue(e.getEdits().isEmpty());
  }
}
