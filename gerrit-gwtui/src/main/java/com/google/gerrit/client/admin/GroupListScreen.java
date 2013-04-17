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

import static com.google.gerrit.common.PageLinks.ADMIN_GROUPS;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.FilteredUserInterface;
import com.google.gerrit.client.ui.IgnoreOutdatedFilterResultsCallbackWrapper;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupListScreen extends AccountScreen implements FilteredUserInterface {
  private GroupTable groups;
  private NpTextBox filterTxt;
  private String subname;

  public GroupListScreen() {
  }

  public GroupListScreen(String params) {
    for (String kvPair : params.split("[,;&]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("filter".equals(kv[0])) {
        subname = URL.decodeQueryString(kv[1]);
      }
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display();
    refresh();
  }

  private void refresh() {
    setToken(subname == null || "".equals(subname) ? ADMIN_GROUPS
        : ADMIN_GROUPS + "?filter=" + URL.encodeQueryString(subname));
    GroupMap.match(subname,
        new IgnoreOutdatedFilterResultsCallbackWrapper<GroupMap>(this,
            new GerritCallback<GroupMap>() {
              @Override
              public void onSuccess(GroupMap result) {
                groups.display(result, subname);
                groups.finishDisplay();
              }
            }));
  }

  @Override
  public String getCurrentFilter() {
    return subname;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());
    initPageHeader();

    groups = new GroupTable(PageLinks.ADMIN_GROUPS);
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
    if (subname != null) {
      filterTxt.setCursorPos(subname.length());
    }
    filterTxt.setFocus(true);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    groups.setRegisterKeys(true);
  }
}
