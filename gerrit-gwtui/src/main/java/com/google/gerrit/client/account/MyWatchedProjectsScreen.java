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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.common.data.AccountProjectWatchInfo;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;

import java.util.List;

public class MyWatchedProjectsScreen extends SettingsScreen {
  private MyWatchesTable watchesTab;
  private Button delSel;
  private ProjectWatchKeyEditor editor;

  @Override
  protected void onInitUI() {
    super.onInitUI();
    watchesTab = new MyWatchesTable();

    delSel = new Button(Util.C.buttonDeleteSshKey());
    delSel.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        watchesTab.deleteChecked();
      }
    });

    /* top table */

    editor = new ProjectWatchKeyEditor(true, false) {
      public void onBrowse() {
        displayPopup();
      }

      public void onUpdate(AccountProjectWatchInfo info) {
        watchesTab.insertWatch(info);
      }

      protected void setPopupPosition(int offsetWidth, int offsetHeight) {
        int top = getAbsoluteTop() - 45; // under page header

        // Try to place it to the right of everything else, but not
        // right justified
        int left = 5 + Math.max(
                         getAbsoluteLeft() + getOffsetWidth(),
                   watchesTab.getAbsoluteLeft() + watchesTab.getOffsetWidth() );

        if (top + offsetHeight > Window.getClientHeight()) {
          top = Window.getClientHeight() - offsetHeight;
        }
        if (left + offsetWidth > Window.getClientWidth()) {
          left = Window.getClientWidth() - offsetWidth;
        }

        if (top < 0) {
          sp.setHeight((sp.getOffsetHeight() + top) + "px");
          top = 0;
        }
        if (left < 0) {
          sp.setWidth((sp.getOffsetWidth() + left) + "px");
          left = 0;
        }
        popup.setPopupPosition(left, top);
      }
    };
    add(editor);


    /* bottom table */

    add(watchesTab);
    add(delSel);

  }

  @Override
  protected void onLoad() {
    super.onLoad();
    populateWatches();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    editor.closePopup();
  }

  protected void populateWatches() {
    Util.ACCOUNT_SVC.myProjectWatch(
        new ScreenLoadCallback<List<AccountProjectWatchInfo>>(this) {
      @Override
      public void preDisplay(final List<AccountProjectWatchInfo> result) {
        watchesTab.display(result);
      }
    });
  }
}
