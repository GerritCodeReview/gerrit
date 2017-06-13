// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.dashboards.DashboardList;
import com.google.gerrit.client.dashboards.DashboardsTable;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.ui.FlowPanel;

public class ProjectDashboardsScreen extends ProjectScreen {
  private DashboardsTable dashes;
  Project.NameKey project;

  public ProjectDashboardsScreen(Project.NameKey project) {
    super(project);
    this.project = project;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    DashboardList.all(
        getProjectKey(),
        new ScreenLoadCallback<JsArray<DashboardList>>(this) {
          @Override
          protected void preDisplay(JsArray<DashboardList> result) {
            dashes.display(result);
          }
        });
    savedPanel = DASHBOARDS;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    dashes = new DashboardsTable(project);
    FlowPanel fp = new FlowPanel();
    fp.add(dashes);
    add(fp);
    dashes.setSavePointerId("dashboards/project/" + getProjectKey().get());
    display();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    dashes.setRegisterKeys(true);
  }
}
