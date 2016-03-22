// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.info.FileInfo;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * A screen that displays the diff of all files in a single page.
 */
public class AllDiffScreen extends Screen {

  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final DiffView diffScreenType;

  public AllDiffScreen(
      PatchSet.Id base, PatchSet.Id revision, DiffView diffScreenType) {
    this.base = base;
    this.revision = revision;
    this.diffScreenType = diffScreenType;
  }

  @Override
  public void onLoad() {
    super.onLoad();

    DiffApi.list(revision,
        base,
        new AsyncCallback<NativeMap<FileInfo>>() {
          @Override
          public void onSuccess(NativeMap<FileInfo> m) {
            JsArray<FileInfo> array = m.values();
            FileInfo.sortFileInfoByPath(array);
            for (FileInfo info : Natives.asList(array)) {
              add(diffScreenType == DiffView.UNIFIED_DIFF
                  ? new Unified(base, revision, info.path(), DisplaySide.B, 0)
                  : new SideBySide(base, revision, info.path(), DisplaySide.B, 0));
            }
            display();
          }

          @Override
          public void onFailure(Throwable caught) {
          }
        });
  }
}
