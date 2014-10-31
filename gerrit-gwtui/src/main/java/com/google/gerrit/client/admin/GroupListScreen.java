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
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.IgnoreOutdatedFilterResultsCallbackWrapper;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupListScreen extends AccountScreen implements FilteredUserInterface {
  private Hyperlink prev;
  private Hyperlink next;
  private GroupTable groups;
  private NpTextBox filterTxt;
  private String subname = "";
  private int startPosition;
  private int pageSize;

  public GroupListScreen() {
    configurePageSize();
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

      if ("skip".equals(kv[0]) && URL.decodeQueryString(kv[1]).matches("^[\\d]+")) {
        startPosition = Integer.parseInt(URL.decodeQueryString(kv[1]));
      }
    }
    configurePageSize();
  }

  private void configurePageSize() {
    if (Gerrit.isSignedIn()) {
      final AccountGeneralPreferences p =
          Gerrit.getUserAccount().getGeneralPreferences();
      final short m = p.getMaximumPageSize();
      pageSize = 0 < m ? m : AccountGeneralPreferences.DEFAULT_PAGESIZE;
    } else {
      pageSize = AccountGeneralPreferences.DEFAULT_PAGESIZE;
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    display();
    refresh(false, false);
  }

  private void refresh(final boolean open, final boolean filterModified) {
    if (filterModified){
      startPosition = 0;
    }
    setToken(getTokenForScreen(subname, startPosition));
    // Retrieve one more group than page size to determine if there are more
    // groups to display
    GroupMap.match(subname, pageSize + 1, startPosition,
        new IgnoreOutdatedFilterResultsCallbackWrapper<>(this,
            new GerritCallback<GroupMap>() {
              @Override
              public void onSuccess(GroupMap result) {
                if (open && result.values().length() > 0) {
                  Gerrit.display(PageLinks.toGroup(
                      result.values().get(0).getGroupUUID()));
                } else {
                  if (result.size() <= pageSize) {
                    groups.display(result, subname);
                    next.setVisible(false);
                  } else {
                    groups.displaySubset(result, 0, result.size() - 1, subname);
                    setupNavigationLink(next, subname, startPosition + pageSize);
                  }
                  if (startPosition > 0) {
                    setupNavigationLink(prev, subname, startPosition - pageSize);
                  } else {
                    prev.setVisible(false);
                  }
                  groups.finishDisplay();
                }
              }
            }));
  }

  private void setupNavigationLink(Hyperlink link, String filter, int skip) {
    link.setTargetHistoryToken(getTokenForScreen(filter, skip));
    link.setVisible(true);
  }

  private String getTokenForScreen(String filter, int skip) {
    String token = ADMIN_GROUPS;
    if (filter != null && !filter.isEmpty()) {
      token += "?filter=" + URL.encodeQueryString(filter);
    }
    if (skip > 0) {
      if (token.contains("?filter=")) {
        token += ",";
      } else {
        token += "?";
      }
      token += "skip=" + skip;
    }
    return token;
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

    prev = new Hyperlink(Util.C.pagedListPrev(), true, "");
    prev.setVisible(false);

    next = new Hyperlink(Util.C.pagedListNext(), true, "");
    next.setVisible(false);

    groups = new GroupTable(PageLinks.ADMIN_GROUPS);
    add(groups);

    final HorizontalPanel buttons = new HorizontalPanel();
    buttons.setStyleName(Gerrit.RESOURCES.css().changeTablePrevNextLinks());
    buttons.add(prev);
    buttons.add(next);
    add(buttons);
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
        boolean enterPressed =
            event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER;
        boolean filterModified = !filterTxt.getValue().equals(subname);
        if (enterPressed || filterModified) {
          subname = filterTxt.getValue();
          refresh(enterPressed, filterModified);
        }
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
