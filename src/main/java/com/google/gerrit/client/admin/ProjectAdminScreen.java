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
import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.LazyPanel;
import com.google.gwt.user.client.ui.TabPanel;

import java.util.ArrayList;
import java.util.List;

public class ProjectAdminScreen extends AccountScreen {
  static final String INFO_TAB = "info";
  static final String BRANCH_TAB = "branches";
  static final String ACCESS_TAB = "access";

  private final Project.Id projectId;
  private final String initialTabToken;

  private List<String> tabTokens;
  private TabPanel tabs;

  public ProjectAdminScreen(final Project.Id toShow, final String token) {
    projectId = toShow;
    initialTabToken = token;
  }

  @Override
  public void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectDetail(projectId,
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
        return new ProjectInfoPanel(projectId);
      }
    }, Util.C.projectAdminTabGeneral());
    tabTokens.add(Link.toProjectAdmin(projectId, INFO_TAB));

    if (!ProjectRight.WILD_PROJECT.equals(projectId)) {
      tabs.add(new LazyPanel() {
        @Override
        protected ProjectBranchesPanel createWidget() {
          return new ProjectBranchesPanel(projectId);
        }
      }, Util.C.projectAdminTabBranches());
      tabTokens.add(Link.toProjectAdmin(projectId, BRANCH_TAB));
    }

    tabs.add(new LazyPanel() {
      @Override
      protected ProjectRightsPanel createWidget() {
        return new ProjectRightsPanel(projectId);
      }
    }, Util.C.projectAdminTabAccess());
    tabTokens.add(Link.toProjectAdmin(projectId, ACCESS_TAB));

    tabs.addSelectionHandler(new SelectionHandler<Integer>() {
      @Override
      public void onSelection(final SelectionEvent<Integer> event) {
        Gerrit.display(tabTokens.get(event.getSelectedItem()), false);
      }
    });
  }


  private void display(final ProjectDetail result) {
    final Project project = result.project;
    setPageTitle(Util.M.project(project.getName()));
  }
}
