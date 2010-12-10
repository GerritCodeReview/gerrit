// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.Status;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;

import java.util.List;

public class ChangeProjectStatusTable extends NavigationTable<ProjectData> {

  private ListBoxChangeListener listBoxChangeListener;

  public ChangeProjectStatusTable() {
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
        .projectListOpen()));

    table.setText(0, 1, Util.C.projectName());
    table.setText(0, 2, Util.C.projectDescription());
    table.setText(0, 3, Util.C.projectStatus());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
  }

  protected MyFlexTable createFlexTable() {
    MyFlexTable table = new MyFlexTable(this);
    table.sinkEvents(Event.ONDBLCLICK | Event.ONCLICK);
    return table;
  }

  public void display(final List<ProjectData> projects) {
    while (1 < table.getRowCount())
      table.removeRow(table.getRowCount() - 1);

    for (final ProjectData k : projects) {
      if (k.canBeUpdated()
          && !k.getNameKey().equals(Gerrit.getConfig().getWildProject())) {
        insert(table.getRowCount(), k);
      }
    }

    finishDisplay();
  }

  protected void insert(final int row, final ProjectData k) {
    table.insertRow(row);

    applyDataRowStyle(row);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().cPROJECT());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

    populate(row, k);
  }

  @Override
  protected Object getRowItemKey(ProjectData item) {
    return item.getNameKey();
  }

  @Override
  protected void onOpenRow(int row) {
    History.newItem(link(getRowItem(row)));
  }

  private String link(final ProjectData item) {
    return Dispatcher.toProjectAdmin(item.getNameKey(), ProjectScreen.INFO);
  }

  protected void populate(final int row, final ProjectData k) {

    table.setWidget(row, 1, new Hyperlink(k.getName(), link(k)));
    table.setText(row, 2, k.getDescription());

    /* Add a listBox widget for each row, except for "All projects" row */
    final ListBox statusList = new ListBox();
    for (final Project.Status type : Project.Status.values()) {
      if (!type.equals(Status.EMPTY)) {
        if (type.equals(Status.PRUNE)) {
          // Shows Prune Status only for administrators users.
          if (k.canBePruned()) {
            statusList.addItem(Util.toLongString(type), type.name());
          }
        } else {
          statusList.addItem(Util.toLongString(type), type.name());
        }
      }
    }

    statusList.addChangeHandler(new ChangeHandler() {
      @Override
      public void onChange(ChangeEvent event) {
        final int id = statusList.getSelectedIndex();
        final Status newStatus =
            Project.Status.valueOf(statusList.getValue(id));

        if (listBoxChangeListener != null) {
          listBoxChangeListener.onChange(k, newStatus);
        }
      }
    });

    setStatus(statusList, k.getStatus());
    table.setWidget(row, 3, statusList);

    setRowItem(row, k);
  }

  private void setStatus(final ListBox statusList,
      final Project.Status newStatus) {
    if (statusList != null) {
      for (int i = 0; i < statusList.getItemCount(); i++) {
        if (newStatus.name().equals(statusList.getValue(i))) {
          statusList.setSelectedIndex(i);
          if (newStatus.equals(Status.PRUNE)) {
            statusList.setEnabled(false);
          }
          return;
        }
      }
      statusList.setSelectedIndex(-1);
    }
  }

  public void disableStatusList(final List<ProjectData> projectsUpdated) {
    for (final ProjectData p : projectsUpdated) {
      ListBox statusList = null;
      final int projectRowCount = table.getRowCount();

      for (int row = 1; row < projectRowCount; row++) {
        final ProjectData project = getRowItem(row);
        if ((project != null) && (project.getName().equals(p.getName()))) {
          final Widget widget = table.getWidget(row, 3);
          if (widget instanceof ListBox) {
            statusList = (ListBox) widget;
            if (p.getStatus().equals(Status.PRUNE)) {
              statusList.setEnabled(false);
            }
          }
        }
      }
    }
  }

  public void setListBoxChangeListener(
      ListBoxChangeListener listBoxChangeListener) {
    this.listBoxChangeListener = listBoxChangeListener;
  }

  /** Listener to listBox onChange event. */
  public static interface ListBoxChangeListener {
    public void onChange(final ProjectData projectData, final Status newStatus);
  }
}
