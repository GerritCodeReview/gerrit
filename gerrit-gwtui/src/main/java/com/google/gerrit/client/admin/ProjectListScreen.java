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
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.FilteredUserInterface;
import com.google.gerrit.client.ui.HighlightingInlineHyperlink;
import com.google.gerrit.client.ui.IgnoreOutdatedFilterResultsCallbackWrapper;
import com.google.gerrit.client.ui.ProjectSearchLink;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class ProjectListScreen extends Screen implements FilteredUserInterface {
  private ProjectsTable projects;
  private NpTextBox filterTxt;
  private String subname;

  public ProjectListScreen() {
  }

  public ProjectListScreen(String params) {
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
    setToken(subname == null || "".equals(subname) ? ADMIN_PROJECTS
        : ADMIN_PROJECTS + "?filter=" + URL.encodeQueryString(subname));
    ProjectMap.match(subname,
        new IgnoreOutdatedFilterResultsCallbackWrapper<ProjectMap>(this,
            new GerritCallback<ProjectMap>() {
              @Override
              public void onSuccess(ProjectMap result) {
                projects.display(result);
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
    setPageTitle(Util.C.projectListTitle());
    initPageHeader();

    projects = new ProjectsTable() {
      @Override
      protected void initColumnHeaders() {
        super.initColumnHeaders();
        if (Gerrit.getGitwebLink() != null) {
          table.setText(0, 3, Util.C.projectRepoBrowser());
          table.getFlexCellFormatter().
            addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
        }
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
        if (Gerrit.getGitwebLink() != null) {
          table.getFlexCellFormatter().
            addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
        }
      }

      @Override
      protected void populate(final int row, final ProjectInfo k) {
        FlowPanel fp = new FlowPanel();
        fp.add(new ProjectSearchLink(k.name_key()));
        fp.add(new HighlightingInlineHyperlink(k.name(), link(k), subname));
        table.setWidget(row, 1, fp);
        table.setText(row, 2, k.description());
        GitwebLink l = Gerrit.getGitwebLink();
        if (l != null) {
          table.setWidget(row, 3, new Anchor(l.getLinkName(), false, l.toProject(k
              .name_key())));
        }

        setRowItem(row, k);
      }
    };
    projects.setSavePointerId(PageLinks.ADMIN_PROJECTS);

    add(projects);
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
    projects.setRegisterKeys(true);
  }
}
