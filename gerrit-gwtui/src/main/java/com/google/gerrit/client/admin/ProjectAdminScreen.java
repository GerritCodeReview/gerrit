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
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.LazyPanel;
import com.google.gwt.user.client.ui.TabPanel;

import java.util.ArrayList;
import java.util.List;

public class ProjectAdminScreen extends Screen {
  static final String INFO_TAB = "info";
  static final String BRANCH_TAB = "branches";
  static final String ACCESS_TAB = "access";
  static final String NEW_ACCESS_TAB = "new access";

  private final Project.NameKey projectName;
  private final String initialTabToken;

  private List<String> tabTokens;
  private TabPanel tabs;

  public ProjectAdminScreen(final Project.NameKey toShow, final String token) {
    projectName = toShow;
    initialTabToken = token;
  }

  @Override
  public boolean displayToken(String token) {
    final int tabIdx = tabTokens.indexOf(token);
    if (0 <= tabIdx) {
      tabs.selectTab(tabIdx);
      setToken(token);
      return true;
    } else {
      return false;
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectDetail(projectName,
        new ScreenLoadCallback<ProjectDetail>(this) {
          @Override
          protected void preDisplay(final ProjectDetail result) {
            display(result);
            tabs.selectTab(tabTokens.indexOf(initialTabToken));
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    tabTokens = new ArrayList<String>();
    tabs = new TabPanel();
    tabs.setWidth("98%");
    add(tabs);

    tabs.add(new LazyPanel() {
      @Override
      protected ProjectInfoPanel createWidget() {
        return new ProjectInfoPanel(projectName);
      }
    }, Util.C.projectAdminTabGeneral());
    tabTokens.add(Dispatcher.toProjectAdmin(projectName, INFO_TAB));

    if (!Gerrit.getConfig().getWildProject().equals(projectName)) {
      tabs.add(new LazyPanel() {
        @Override
        protected ProjectBranchesPanel createWidget() {
          return new ProjectBranchesPanel(projectName);
        }
      }, Util.C.projectAdminTabBranches());
      tabTokens.add(Dispatcher.toProjectAdmin(projectName, BRANCH_TAB));
    }

    tabs.add(new LazyPanel() {
      @Override
      protected ProjectRightsPanel createWidget() {
        return new ProjectRightsPanel(projectName);
      }
    }, Util.C.projectAdminTabAccess());
    tabTokens.add(Dispatcher.toProjectAdmin(projectName, ACCESS_TAB));

    tabs.add(new LazyPanel() {
      @Override
      protected NewProjectRightsPanel createWidget() {
        return new NewProjectRightsPanel(projectName);
      }
    }, Util.C.projectAdminNewTabAccess());
    tabTokens.add(Dispatcher.toProjectAdmin(projectName, NEW_ACCESS_TAB));

    tabs.addSelectionHandler(new SelectionHandler<Integer>() {
      @Override
      public void onSelection(final SelectionEvent<Integer> event) {
        setToken(tabTokens.get(event.getSelectedItem()));
      }
    });
  }


  private void display(final ProjectDetail result) {
    final Project project = result.project;
    setPageTitle(Util.M.project(project.getName()));
  }
}
