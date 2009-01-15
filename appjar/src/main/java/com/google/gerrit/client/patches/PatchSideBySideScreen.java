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

import com.google.gerrit.client.data.SideBySidePatchDetail;
import com.google.gerrit.client.reviewdb.Patch;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gwt.user.client.ui.FlowPanel;

public class PatchSideBySideScreen extends PatchScreen {
  private SideBySideTable sbsTable;

  public PatchSideBySideScreen(final Patch.Key id) {
    super(id);
  }

  @Override
  public void onLoad() {
    if (sbsTable == null) {
      initUI();
    }

    super.onLoad();

    PatchUtil.DETAIL_SVC.sideBySidePatchDetail(patchId,
        new ScreenLoadCallback<SideBySidePatchDetail>() {
          public void onSuccess(final SideBySidePatchDetail r) {
            // TODO Actually we want to cancel the RPC if detached.
            if (isAttached()) {
              display(r);
            }
          }
        });
  }

  private void initUI() {
    final FlowPanel sbsPanel = new FlowPanel();
    sbsPanel.setStyleName("gerrit-SideBySideScreen-SideBySideTable");
    sbsTable = new SideBySideTable();
    sbsPanel.add(sbsTable);
    add(sbsPanel);
  }

  private void display(final SideBySidePatchDetail detail) {
    sbsTable.setAccountInfoCache(detail.getAccounts());
    sbsTable.display(detail);
    sbsTable.finishDisplay(true);
  }
}
