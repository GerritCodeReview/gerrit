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

import static com.google.gerrit.common.PageLinks.ADMIN_PROJECTS;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.WebLinkInfo;
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.HighlightingInlineHyperlink;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.ProjectSearchLink;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

import java.util.List;

public class ProjectListScreen extends Screen {
  private Hyperlink prev;
  private Hyperlink next;
  private ProjectsTable projects;
  private NpTextBox filterTxt;
  private int pageSize;

  private String match = "";
  private int start;
  private Query query;

  public ProjectListScreen() {
    configurePageSize();
  }

  public ProjectListScreen(String params) {
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
    query = new Query(match).start(start).run();
  }

  private void setupNavigationLink(Hyperlink link, String filter, int skip) {
    link.setTargetHistoryToken(getTokenForScreen(filter, skip));
    link.setVisible(true);
  }

  private String getTokenForScreen(String filter, int skip) {
    String token = ADMIN_PROJECTS;
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
    setPageTitle(Util.C.projectListTitle());
    initPageHeader();

    prev = new Hyperlink(Util.C.pagedListPrev(), true, "");
    prev.setVisible(false);

    next = new Hyperlink(Util.C.pagedListNext(), true, "");
    next.setVisible(false);

    projects = new ProjectsTable() {
      @Override
      protected void initColumnHeaders() {
        super.initColumnHeaders();
        table.setText(0, ProjectsTable.C_REPO_BROWSER,
            Util.C.projectRepoBrowser());
        table.getFlexCellFormatter().
          addStyleName(0, ProjectsTable.C_REPO_BROWSER,
              Gerrit.RESOURCES.css().dataHeader());
      }

      @Override
      protected void onOpenRow(final int row) {
        History.newItem(link(getRowItem(row)));
      }

      private String link(final ProjectInfo item) {
        return Dispatcher.toProject(item.name_key());
      }

      @Override
      protected void insert(int row, ProjectInfo k) {
        super.insert(row, k);
        table.getFlexCellFormatter().addStyleName(row,
            ProjectsTable.C_REPO_BROWSER, Gerrit.RESOURCES.css().dataCell());
      }

      @Override
      protected void populate(final int row, final ProjectInfo k) {
        Image state = new Image();
        switch (k.state()) {
          case HIDDEN:
            state.setResource(Gerrit.RESOURCES.redNot());
            state.setTitle(Util.toLongString(k.state()));
            table.setWidget(row, ProjectsTable.C_STATE, state);
            break;
          case READ_ONLY:
            state.setResource(Gerrit.RESOURCES.readOnly());
            state.setTitle(Util.toLongString(k.state()));
            table.setWidget(row, ProjectsTable.C_STATE, state);
            break;
          default:
            // Intentionally left blank, do not show an icon when active.
            break;
        }

        FlowPanel fp = new FlowPanel();
        fp.add(new ProjectSearchLink(k.name_key()));
        fp.add(new HighlightingInlineHyperlink(k.name(), link(k), match));
        table.setWidget(row, ProjectsTable.C_NAME, fp);
        table.setText(row, ProjectsTable.C_DESCRIPTION, k.description());
        addWebLinks(row, k);

        setRowItem(row, k);
      }

      private void addWebLinks(int row, ProjectInfo k) {
        GitwebLink gitWebLink = Gerrit.getGitwebLink();
        List<WebLinkInfo> webLinks = Natives.asList(k.webLinks());
        if (gitWebLink != null || (webLinks != null && !webLinks.isEmpty())) {
          FlowPanel p = new FlowPanel();
          table.setWidget(row, ProjectsTable.C_REPO_BROWSER, p);

          if (gitWebLink != null) {
            Anchor a = new Anchor();
            a.setText(gitWebLink.getLinkName());
            a.setHref(gitWebLink.toProject(k.name_key()));
            p.add(a);
          }
          if (webLinks != null) {
            for (WebLinkInfo weblink : webLinks) {
              p.add(weblink.toAnchor());
            }
          }
        }
      }
    };
    projects.setSavePointerId(PageLinks.ADMIN_PROJECTS);

    add(projects);
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
    filterTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        Query q = new Query(filterTxt.getValue())
          .open(event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER);
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
    projects.setRegisterKeys(true);
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
      ProjectMap.match(qMatch, limit, qStart,
          new GerritCallback<ProjectMap>() {
            @Override
            public void onSuccess(ProjectMap result) {
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

    private void showMap(ProjectMap result) {
      if (open && !result.isEmpty()) {
        Gerrit.display(PageLinks.toProject(result.values().get(0).name_key()));
        return;
      }

      setToken(getTokenForScreen(qMatch, qStart));
      ProjectListScreen.this.match = qMatch;
      ProjectListScreen.this.start = qStart;

      if (result.size() <= pageSize) {
        projects.display(result);
        next.setVisible(false);
      } else {
        projects.displaySubset(result, 0, result.size() - 1);
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
