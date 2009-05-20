// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.reviewdb.Patch;

public abstract class PatchLink extends DirectScreenLink {
  protected Patch.Key patchKey;
  protected int patchIndex;
  protected PatchTable parentPatchTable;

  /**
   * @param text The text of this link
   * @param patchKey The key for this patch
   * @param patchIndex The index of the current patch in the patch set
   * @param historyToken The history token
   * @param parentPatchTable The table used to display this link
   */
  public PatchLink(final String text, final Patch.Key patchKey, final int patchIndex,
      final String historyToken, PatchTable parentPatchTable) {
    super(text, historyToken);
    this.patchKey = patchKey;
    this.patchIndex = patchIndex;
    this.parentPatchTable = parentPatchTable;
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(final String text, final Patch.Key patchKey, final int patchIndex,
        PatchTable parentPatchTable) {
      super(text, patchKey, patchIndex, Link.toPatchSideBySide(patchKey), parentPatchTable);
    }

    @Override
    protected Screen createScreen() {
      return new PatchScreen.SideBySide(patchKey, patchIndex, parentPatchTable);
    }
  }

  public static class Unified extends PatchLink {
    public Unified(final String text, final Patch.Key patchKey, final int patchIndex,
        PatchTable parentPatchTable) {
      super(text, patchKey, patchIndex, Link.toPatchUnified(patchKey), parentPatchTable);
    }

    @Override
    protected Screen createScreen() {
      return new PatchScreen.Unified(patchKey, patchIndex, parentPatchTable);
    }
  }
}
