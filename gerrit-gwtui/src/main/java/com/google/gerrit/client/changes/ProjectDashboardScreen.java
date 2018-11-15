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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.user.client.ui.FlowPanel;

public class ProjectDashboardScreen extends ProjectScreen implements ChangeListScreen {
  private DashboardTable table;
  private String params;

  public ProjectDashboardScreen(Project.NameKey toShow, String params) {
    super(toShow);
    this.params = params;
  }

  @Override
  protected void onInitUI() {
    table =
        new DashboardTable(this, params) {
          @Override
          public void finishDisplay() {
            super.finishDisplay();
            display();
          }
        };

    super.onInitUI();

    String title = table.getTitle();
    if (title != null) {
      FlowPanel fp = new FlowPanel();
      fp.setStyleName(Gerrit.RESOURCES.css().screenHeader());
      fp.add(new InlineHyperlink(title, PageLinks.toCustomDashboard(params)));
      add(fp);
    }

    add(table);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }
}
