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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupListScreen extends AccountScreen {
  private GroupTable groups;
  private NpTextBox filterTxt;
  private String subname;

  @Override
  protected void onLoad() {
    super.onLoad();
    refresh();
  }

  private void refresh() {
    final String mySubname = subname;
    GroupMap.match(subname, new ScreenLoadCallback<GroupMap>(this) {
      @Override
      protected void preDisplay(final GroupMap result) {
        if ((mySubname == null && subname == null)
            || (mySubname != null && mySubname.equals(subname))) {
          display(result);
        }
        // Else ignore the result, the user has already changed subname and
        // the result is not relevant anymore. If multiple RPC's are fired
        // the results may come back out-of-order and a non-relevant result
        // could overwrite the correct result if not ignored.
      }
    });
  }

  private void display(final GroupMap result) {
    groups.display(result, subname);
    groups.finishDisplay();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());
    initPageHeader();

    groups = new GroupTable(true /* hyperlink to admin */, PageLinks.ADMIN_GROUPS);
    add(groups);
  }

  private void initPageHeader() {
    final HorizontalPanel hp = new HorizontalPanel();
    hp.setStyleName(Gerrit.RESOURCES.css().projectFilterPanel());
    final Label filterLabel = new Label(Util.C.projectFilter());
    filterLabel.setStyleName(Gerrit.RESOURCES.css().projectFilterLabel());
    hp.add(filterLabel);
    filterTxt = new NpTextBox();
    filterTxt.setValue(subname);
    filterTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        subname = filterTxt.getValue();
        refresh();
      }
    });
    hp.add(filterTxt);
    add(hp);
  }

  @Override
  public void onShowView() {
    super.onShowView();
    filterTxt.setFocus(true);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    groups.setRegisterKeys(true);
  }
}
