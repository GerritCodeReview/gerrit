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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.projects.ProjectInfo;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.HighlightingInlineHyperlink;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.client.ui.ProjectsTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class ProjectListScreen extends Screen {
  private ProjectsTable projects;
  private NpTextBox filterTxt;
  private String subname;

  @Override
  protected void onLoad() {
    super.onLoad();
    if (subname == null || "".equals(subname)) {
      ProjectMap.all(new ScreenLoadCallback<ProjectMap>(this) {
        @Override
        protected void preDisplay(final ProjectMap result) {
          display(result);
        }
      });
    } else {
      ProjectMap.match(subname, new ScreenLoadCallback<ProjectMap>(this) {
        @Override
        protected void preDisplay(final ProjectMap result) {
          display(result);
        }
      });
    }
  }

  private void display(final ProjectMap result) {
    projects.display(result);
    projects.finishDisplay();
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
        return Dispatcher.toProjectAdmin(item.name_key(), ProjectScreen.INFO);
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
        fp.add(createSearchLink(k));
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

      private Widget createSearchLink(final ProjectInfo projectInfo) {
        Image image = new Image(Gerrit.RESOURCES.queryProjectLink());
        InlineHyperlink h = new InlineHyperlink(" ",
            PageLinks.toProjectDashboard(projectInfo.name_key(), "default"));
        h.setTitle(Util.C.projectListQueryLink());
        DOM.insertBefore(h.getElement(), image.getElement(),
            DOM.getFirstChild(h.getElement()));

        return h;
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
        onLoad();
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
    projects.setRegisterKeys(true);
  }
}
