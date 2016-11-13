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
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.PagingHyperlink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class GroupListScreen extends Screen {
  private Hyperlink prev;
  private Hyperlink next;
  private GroupTable groups;
  private NpTextBox filterTxt;
  private int pageSize;

  private String match = "";
  private int start;
  private Query query;

  public GroupListScreen() {
    setRequiresSignIn(true);
    pageSize = Gerrit.getUserPreferences().changesPerPage();
  }

  public GroupListScreen(String params) {
    this();
    for (String kvPair : params.split("[,;&]")) {
      String[] kv = kvPair.split("=", 2);
      if (kv.length != 2 || kv[0].isEmpty()) {
        continue;
      }

      if ("filter".equals(kv[0])) {
        match = URL.decodeQueryString(kv[1]);
      }

      if ("skip".equals(kv[0]) && URL.decodeQueryString(kv[1]).matches("^[\\d]+")) {
        start = Integer.parseInt(URL.decodeQueryString(kv[1]));
      }
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    query = new Query(match).start(start).run();
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
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());
    initPageHeader();

    prev = PagingHyperlink.createPrev();
    prev.setVisible(false);

    next = PagingHyperlink.createNext();
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
    filterTxt.setValue(match);
    filterTxt.addKeyUpHandler(
        new KeyUpHandler() {
          @Override
          public void onKeyUp(KeyUpEvent event) {
            Query q =
                new Query(filterTxt.getValue())
                    .open(event.getNativeKeyCode() == KeyCodes.KEY_ENTER);
            if (match.equals(q.qMatch)) {
              q.start(start);
            }
            if (q.open || !match.equals(q.qMatch)) {
              if (query == null) {
                q.run();
              }
              query = q;
            }
          }
        });
    hp.add(filterTxt);
    add(hp);
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (match != null) {
      filterTxt.setCursorPos(match.length());
    }
    filterTxt.setFocus(true);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    groups.setRegisterKeys(true);
  }

  private class Query {
    private final String qMatch;
    private int qStart;
    private boolean open;

    Query(String match) {
      this.qMatch = match;
    }

    Query start(int start) {
      this.qStart = start;
      return this;
    }

    Query open(boolean open) {
      this.open = open;
      return this;
    }

    Query run() {
      int limit = open ? 1 : pageSize + 1;
      GroupMap.match(
          qMatch,
          limit,
          qStart,
          new GerritCallback<GroupMap>() {
            @Override
            public void onSuccess(GroupMap result) {
              if (!isAttached()) {
                // View has been disposed.
              } else if (query == Query.this) {
                query = null;
                showMap(result);
              } else {
                query.run();
              }
            }
          });
      return this;
    }

    private void showMap(GroupMap result) {
      if (open && !result.isEmpty()) {
        Gerrit.display(PageLinks.toGroup(result.values().get(0).getGroupUUID()));
        return;
      }

      setToken(getTokenForScreen(qMatch, qStart));
      GroupListScreen.this.match = qMatch;
      GroupListScreen.this.start = qStart;

      if (result.size() <= pageSize) {
        groups.display(result, qMatch);
        next.setVisible(false);
      } else {
        groups.displaySubset(result, 0, result.size() - 1, qMatch);
        setupNavigationLink(next, qMatch, qStart + pageSize);
      }

      if (qStart > 0) {
        setupNavigationLink(prev, qMatch, qStart - pageSize);
      } else {
        prev.setVisible(false);
      }

      if (!isCurrentView()) {
        display();
      }
    }
  }
}
