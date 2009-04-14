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
import com.google.gerrit.client.ui.LazyTabChild;
import com.google.gwt.user.client.ui.SourcesTabEvents;
import com.google.gwt.user.client.ui.TabListener;
import com.google.gwt.user.client.ui.TabPanel;

import java.util.ArrayList;
import java.util.List;

public class ProjectAdminScreen extends AccountScreen {
  static final String INFO_TAB = "info";
  static final String BRANCH_TAB = "branches";
  static final String ACCESS_TAB = "access";

  private String initialTabToken;
  private Project.Id projectId;

  private List<String> tabTokens;
  private TabPanel tabs;

  public ProjectAdminScreen(final Project.Id toShow, final String token) {
    projectId = toShow;
    initialTabToken = token;
  }

  @Override
  public void onLoad() {
    if (tabs == null) {
      initUI();
    }

    super.onLoad();
    tabs.selectTab(tabTokens.indexOf(initialTabToken));

    Util.PROJECT_SVC.projectDetail(projectId,
        new ScreenLoadCallback<ProjectDetail>(this) {
          @Override
          protected void prepare(final ProjectDetail result) {
            display(result);
          }
        });
  }

  private void initUI() {
    tabTokens = new ArrayList<String>();
    tabs = new TabPanel();
    tabs.setWidth("98%");
    add(tabs);

    tabs.add(new LazyTabChild<ProjectInfoPanel>() {
      @Override
      protected ProjectInfoPanel create() {
        return new ProjectInfoPanel(projectId);
      }
    }, Util.C.projectAdminTabGeneral());
    tabTokens.add(Link.toProjectAdmin(projectId, INFO_TAB));

    if (!ProjectRight.WILD_PROJECT.equals(projectId)) {
      tabs.add(new LazyTabChild<ProjectBranchesPanel>() {
        @Override
        protected ProjectBranchesPanel create() {
          return new ProjectBranchesPanel(projectId);
        }
      }, Util.C.projectAdminTabBranches());
      tabTokens.add(Link.toProjectAdmin(projectId, BRANCH_TAB));
    }

    tabs.add(new LazyTabChild<ProjectRightsPanel>() {
      @Override
      protected ProjectRightsPanel create() {
        return new ProjectRightsPanel(projectId);
      }
    }, Util.C.projectAdminTabAccess());
    tabTokens.add(Link.toProjectAdmin(projectId, ACCESS_TAB));

    tabs.addTabListener(new TabListener() {
      public boolean onBeforeTabSelected(SourcesTabEvents sender, int tabIndex) {
        return true;
      }

      public void onTabSelected(SourcesTabEvents sender, int tabIndex) {
        Gerrit.display(tabTokens.get(tabIndex), false);
      }
    });
  }


  private void display(final ProjectDetail result) {
    final Project project = result.project;
    setTitleText(Util.M.project(project.getName()));
  }
}
