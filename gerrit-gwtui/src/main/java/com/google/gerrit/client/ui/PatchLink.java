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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.Patch;

public class PatchLink extends InlineHyperlink {
  protected Patch.Key patchKey;
  protected int patchIndex;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable parentPatchTable;
  protected PatchScreen.TopView topView;

  /**
   * @param text The text of this link
   * @param patchKey The key for this patch
   * @param patchIndex The index of the current patch in the patch set
   * @param historyToken The history token
   * @param patchSetDetail Detailed information about the patch set.
   * @param parentPatchTable The table used to display this link
   */
  protected PatchLink(final String text, final Patch.Key patchKey,
      final int patchIndex, final String historyToken,
      final PatchSetDetail patchSetDetail, final PatchTable parentPatchTable,
      final PatchScreen.TopView topView) {
    super(text, historyToken);
    this.patchKey = patchKey;
    this.patchIndex = patchIndex;
    this.patchSetDetail = patchSetDetail;
    this.parentPatchTable = parentPatchTable;
    this.parentPatchTable = parentPatchTable;
    this.topView = topView;
  }

  /**
   * @param text The text of this link
   * @param type The type of the link to create (unified/side-by-side)
   * @param patchScreen The patchScreen to grab contents to link to from
   */
  public PatchLink(final String text, final PatchScreen.Type type,
      final PatchScreen patchScreen) {
    this(text, //
        patchScreen.getPatchKey(), //
        patchScreen.getPatchIndex(), //
        Dispatcher.toPatch(type, patchScreen.getPatchKey()), //
        patchScreen.getPatchSetDetail(), //
        patchScreen.getFileList(), //
        patchScreen.getTopView() //
        );
  }

  @Override
  public void go() {
    Dispatcher.patch( //
        getTargetHistoryToken(), //
        patchKey, //
        patchIndex, //
        patchSetDetail, //
        parentPatchTable,
        topView //
        );
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(final String text, final Patch.Key patchKey,
        final int patchIndex, PatchSetDetail patchSetDetail,
        PatchTable parentPatchTable) {
      super(text, patchKey, patchIndex, Dispatcher.toPatchSideBySide(patchKey),
          patchSetDetail, parentPatchTable, null);
    }
  }

  public static class Unified extends PatchLink {
    public Unified(final String text, final Patch.Key patchKey,
        final int patchIndex, PatchSetDetail patchSetDetail,
        PatchTable parentPatchTable) {
      super(text, patchKey, patchIndex, Dispatcher.toPatchUnified(patchKey),
          patchSetDetail, parentPatchTable, null);
    }
  }
}
