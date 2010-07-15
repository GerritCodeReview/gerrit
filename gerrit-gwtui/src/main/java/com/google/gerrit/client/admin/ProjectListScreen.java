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
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;

import java.util.List;

public class ProjectListScreen extends Screen {
  private ProjectTable projects;

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.visibleProjects(new ScreenLoadCallback<List<Project>>(this) {
      @Override
      protected void preDisplay(final List<Project> result) {
        projects.display(result);
        projects.finishDisplay();
      }
    });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.projectListTitle());

    projects = new ProjectTable();
    add(projects);

    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    fp.add(new SmallHeading(Util.C.headingCreateGroup()));
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    projects.setRegisterKeys(true);
  }

  private class ProjectTable extends NavigationTable<Project> {
    ProjectTable() {
      setSavePointerId(PageLinks.ADMIN_PROJECTS);
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .projectListOpen()));

      table.setText(0, 1, Util.C.columnProjectName());
      table.setText(0, 2, Util.C.columnProjectDescription());
      table.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(final ClickEvent event) {
          final Cell cell = table.getCellForEvent(event);
          if (cell != null && cell.getCellIndex() != 1
              && getRowItem(cell.getRowIndex()) != null) {
            movePointerTo(cell.getRowIndex());
          }
        }
      });

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    }

    @Override
    protected Object getRowItemKey(final Project item) {
      return item.getNameKey();
    }

    @Override
    protected void onOpenRow(final int row) {
      History.newItem(link(getRowItem(row)));
    }

    private String link(final Project item) {
      return Dispatcher.toProjectAdmin(item.getNameKey(), ProjectScreen.INFO);
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
      table.setWidget(row, 1, new Hyperlink(k.getName(), link(k)));
      table.setText(row, 2, k.getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().cPROJECT());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }
  }
}
