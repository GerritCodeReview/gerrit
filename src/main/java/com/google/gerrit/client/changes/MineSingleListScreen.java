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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.SingleListChangeInfo;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gwt.user.client.rpc.AsyncCallback;


public abstract class MineSingleListScreen extends AccountScreen {
  private final String anchor;
  private ChangeTable table;
  private ChangeTable.Section drafts;

  protected MineSingleListScreen(final String historyToken) {
    anchor = historyToken;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new ChangeTable();
    drafts = new ChangeTable.Section();

    table.addSection(drafts);
    table.setSavePointerId(anchor);

    add(table);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  protected AsyncCallback<SingleListChangeInfo> loadCallback() {
    return new ScreenLoadCallback<SingleListChangeInfo>(this) {
      @Override
      protected void preDisplay(final SingleListChangeInfo result) {
        display(result);
      }
    };
  }

  private void display(final SingleListChangeInfo result) {
    table.setAccountInfoCache(result.getAccounts());
    drafts.display(result.getChanges());
    table.finishDisplay();
  }
}
