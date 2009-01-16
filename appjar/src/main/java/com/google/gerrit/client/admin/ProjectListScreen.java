// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.SourcesTableEvents;
import com.google.gwt.user.client.ui.TableListener;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.List;

public class ProjectListScreen extends AccountScreen {
  private ProjectTable projects;

  public ProjectListScreen() {
    super(Util.C.projectListTitle());
  }

  @Override
  public Object getScreenCacheToken() {
    return getClass();
  }

  @Override
  public void onLoad() {
    if (projects == null) {
      initUI();
    }

    Util.PROJECT_SVC.ownedProjects(new GerritCallback<List<Project>>() {
      public void onSuccess(final List<Project> result) {
        if (isAttached()) {
          projects.display(result);
          projects.finishDisplay(true);
        }
      }
    });
  }

  private void initUI() {
    projects = new ProjectTable();
    projects.setSavePointerId(Link.ADMIN_PROJECTS);
    add(projects);

    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName("gerrit-AddSshKeyPanel");
    fp.add(new SmallHeading(Util.C.headingCreateGroup()));
  }

  private class ProjectTable extends FancyFlexTable<Project> {
    ProjectTable() {
      table.setText(0, 1, Util.C.columnProjectName());
      table.setText(0, 2, Util.C.columnProjectDescription());
      table.addTableListener(new TableListener() {
        public void onCellClicked(SourcesTableEvents sender, int row, int cell) {
          if (cell != 1 && getRowItem(row) != null) {
            movePointerTo(row);
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, S_DATA_HEADER);
      fmt.addStyleName(0, 2, S_DATA_HEADER);
    }

    @Override
    protected Object getRowItemKey(final Project item) {
      return item.getId();
    }

    @Override
    protected void onOpenItem(final Project item) {
      History.newItem(Link.toProjectAdmin(item.getId()));
    }

    void display(final List<Project> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final Project k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final Project k) {
      table.setWidget(row, 1, new Hyperlink(k.getName(), Link.toProjectAdmin(k
          .getId())));
      table.setText(row, 2, k.getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, S_DATA_CELL);
      fmt.addStyleName(row, 2, S_DATA_CELL);

      setRowItem(row, k);
    }
  }
}
