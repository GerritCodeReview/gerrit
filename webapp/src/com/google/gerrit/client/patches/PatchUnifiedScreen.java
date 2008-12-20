// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.patches;

import com.google.gerrit.client.data.UnifiedPatchDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.rpc.ScreenLoadCallback;

public class PatchUnifiedScreen extends PatchScreen {
  private UnifiedDiffTable diffTable;

  public PatchUnifiedScreen(final Patch.Id id) {
    super(id);
  }

  @Override
  public void onLoad() {
    if (diffTable == null) {
      initUI();
    }

    super.onLoad();

    PatchUtil.DETAIL_SVC.unifiedPatchDetail(patchId,
        new ScreenLoadCallback<UnifiedPatchDetail>() {
          public void onSuccess(final UnifiedPatchDetail r) {
            // TODO Actually we want to cancel the RPC if detached.
            if (isAttached()) {
              display(r);
            }
          }
        });
  }

  private void initUI() {
    diffTable = new UnifiedDiffTable();
    add(diffTable);
  }

  private void display(final UnifiedPatchDetail detail) {
    diffTable.setPatchKey(detail.getPatch().getKey());
    diffTable.setAccountInfoCache(detail.getAccounts());
    diffTable.display(detail.getLines());
  }
}
