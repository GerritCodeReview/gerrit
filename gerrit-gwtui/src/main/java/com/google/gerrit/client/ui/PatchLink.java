// Copyright (C) 2008 The Android Open Source Project
// Copyright (C) 2014 Digia Plc and/or its subsidiary(-ies).
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
import com.google.gerrit.client.patches.AbstractPatchScreen;
import com.google.gerrit.client.patches.AllInOnePatchScreen;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;

public class PatchLink extends InlineHyperlink {
  protected PatchSet.Id base;
  protected Patch.Key patchKey;
  protected int patchIndex;
  protected PatchSetDetail patchSetDetail;
  protected PatchTable parentPatchTable;
  protected AbstractPatchScreen.TopView topView;

  /**
   * @param text The text of this link
   * @param base optional base to compare against.
   * @param patchKey The key for this patch
   * @param patchIndex The index of the current patch in the patch set
   * @param historyToken The history token
   * @param patchSetDetail Detailed information about the patch set.
   * @param parentPatchTable The table used to display this link
   */
  protected PatchLink(String text, PatchSet.Id base, Patch.Key patchKey,
      int patchIndex, String historyToken,
      PatchSetDetail patchSetDetail, PatchTable parentPatchTable,
      AbstractPatchScreen.TopView topView) {
    super(text, historyToken);
    this.base = base;
    this.patchKey = patchKey;
    this.patchIndex = patchIndex;
    this.patchSetDetail = patchSetDetail;
    this.parentPatchTable = parentPatchTable;
    this.topView = topView;
  }

  /**
   * @param text The text of this link
   * @param type The type of the link to create (unified/side-by-side)
   * @param patchScreen The patchScreen to grab contents to link to from
   */
  public PatchLink(String text, AbstractPatchScreen.Type type, AbstractPatchScreen patchScreen) {
    this(text, //
        patchScreen.getFileList().getBase(), //
        patchScreen.getPatchKey(), //
        patchScreen.getPatchIndex(), //
        Dispatcher.toPatch(type, patchScreen.getPatchKey()), //
        patchScreen.getPatchSetDetail(), //
        patchScreen.getFileList(), //
        patchScreen.getTopView() //
        );
  }

  public static class All extends PatchLink {
    public All(final String text, final AbstractPatchScreen.Type type,
        final AllInOnePatchScreen patchScreen) {
      super(text, //
          patchScreen.getFileList().getBase(),
          patchScreen.getPatchKey(), //
          patchScreen.getPatchIndex(), //
          Dispatcher.toPatchAll(type,
              patchScreen.getPatchKey().getParentKey(),
              patchScreen.getPatchSetDetail()), //
          patchScreen.getPatchSetDetail(), //
          patchScreen.getFileList(), //
          patchScreen.getTopView()  //
          );
    }
  }

  @Override
  public void go() {
    Dispatcher.patch( //
        getTargetHistoryToken(), //
        base, //
        patchKey, //
        patchIndex, //
        patchSetDetail, //
        parentPatchTable,
        topView //
        );
  }

  public static class SideBySide extends PatchLink {
    public SideBySide(String text, PatchSet.Id base, Patch.Key patchKey,
        int patchIndex, PatchSetDetail patchSetDetail,
        PatchTable parentPatchTable) {
      super(text, base, patchKey, patchIndex,
          Dispatcher.toPatchSideBySide(base, patchKey),
          patchSetDetail, parentPatchTable, null);
    }
  }

  public static class Unified extends PatchLink {
    public Unified(String text, PatchSet.Id base, final Patch.Key patchKey,
        int patchIndex, PatchSetDetail patchSetDetail,
        PatchTable parentPatchTable) {
      super(text, base, patchKey, patchIndex,
          Dispatcher.toPatchUnified(base, patchKey),
          patchSetDetail, parentPatchTable, null);
    }
  }
}
