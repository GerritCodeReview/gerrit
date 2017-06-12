// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.FancyFlexTable;
import com.google.gerrit.client.ui.ProjectLink;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import java.util.HashSet;
import java.util.Set;

public class MyWatchesTable extends FancyFlexTable<ProjectWatchInfo> {

  public MyWatchesTable() {
    table.setWidth("");
    table.insertRow(1);
    table.setText(0, 2, Util.C.watchedProjectName());
    table.setText(0, 3, Util.C.watchedProjectColumnEmailNotifications());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    fmt.setRowSpan(0, 0, 2);
    fmt.setRowSpan(0, 1, 2);
    fmt.setRowSpan(0, 2, 2);
    fmt.getElement(0, 3).setPropertyString("align", "center");

    fmt.setColSpan(0, 3, 5);
    table.setText(1, 0, Util.C.watchedProjectColumnNewChanges());
    table.setText(1, 1, Util.C.watchedProjectColumnNewPatchSets());
    table.setText(1, 2, Util.C.watchedProjectColumnAllComments());
    table.setText(1, 3, Util.C.watchedProjectColumnSubmittedChanges());
    table.setText(1, 4, Util.C.watchedProjectColumnAbandonedChanges());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(1, 1, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(1, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(1, 3, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(1, 4, Gerrit.RESOURCES.css().dataHeader());
  }

  public void deleteChecked() {
    final Set<ProjectWatchInfo> infos = getCheckedProjectWatchInfos();
    if (!infos.isEmpty()) {
      AccountApi.deleteWatchedProjects(
          "self",
          infos,
          new GerritCallback<JsArray<ProjectWatchInfo>>() {
            @Override
            public void onSuccess(JsArray<ProjectWatchInfo> watchedProjects) {
              remove(infos);
            }
          });
    }
  }

  protected void remove(Set<ProjectWatchInfo> infos) {
    for (int row = 1; row < table.getRowCount(); ) {
      final ProjectWatchInfo k = getRowItem(row);
      if (k != null && infos.contains(k)) {
        table.removeRow(row);
      } else {
        row++;
      }
    }
  }

  protected Set<ProjectWatchInfo> getCheckedProjectWatchInfos() {
    final Set<ProjectWatchInfo> infos = new HashSet<>();
    for (int row = 1; row < table.getRowCount(); row++) {
      final ProjectWatchInfo k = getRowItem(row);
      if (k != null && ((CheckBox) table.getWidget(row, 1)).getValue()) {
        infos.add(k);
      }
    }
    return infos;
  }

  public void insertWatch(final ProjectWatchInfo k) {
    final String newName = k.project();
    int row = 1;
    for (; row < table.getRowCount(); row++) {
      final ProjectWatchInfo i = getRowItem(row);
      if (i != null && i.project().compareTo(newName) >= 0) {
        break;
      }
    }

    table.insertRow(row);
    applyDataRowStyle(row);
    populate(row, k);
  }

  public void display(final JsArray<ProjectWatchInfo> result) {
    while (2 < table.getRowCount()) {
      table.removeRow(table.getRowCount() - 1);
    }

    for (ProjectWatchInfo info : Natives.asList(result)) {
      final int row = table.getRowCount();
      table.insertRow(row);
      applyDataRowStyle(row);
      populate(row, info);
    }
  }

  protected void populate(final int row, final ProjectWatchInfo info) {
    final FlowPanel fp = new FlowPanel();
    fp.add(new ProjectLink(info.project(), new Project.NameKey(info.project())));
    if (info.filter() != null) {
      Label filter = new Label(info.filter());
      filter.setStyleName(Gerrit.RESOURCES.css().watchedProjectFilter());
      fp.add(filter);
    }

    table.setWidget(row, 1, new CheckBox());
    table.setWidget(row, 2, fp);

    addNotifyButton(ProjectWatchInfo.Type.NEW_CHANGES, info, row, 3);
    addNotifyButton(ProjectWatchInfo.Type.NEW_PATCHSETS, info, row, 4);
    addNotifyButton(ProjectWatchInfo.Type.ALL_COMMENTS, info, row, 5);
    addNotifyButton(ProjectWatchInfo.Type.SUBMITTED_CHANGES, info, row, 6);
    addNotifyButton(ProjectWatchInfo.Type.ABANDONED_CHANGES, info, row, 7);

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
    fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 4, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 5, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 6, Gerrit.RESOURCES.css().dataCell());
    fmt.addStyleName(row, 7, Gerrit.RESOURCES.css().dataCell());

    setRowItem(row, info);
  }

  protected void addNotifyButton(
      final ProjectWatchInfo.Type type, final ProjectWatchInfo info, final int row, final int col) {
    final CheckBox cbox = new CheckBox();

    cbox.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(final ClickEvent event) {
            final Boolean oldVal = info.notify(type);
            info.notify(type, cbox.getValue());
            cbox.setEnabled(false);

            AccountApi.updateWatchedProject(
                "self",
                info,
                new GerritCallback<JsArray<ProjectWatchInfo>>() {
                  @Override
                  public void onSuccess(JsArray<ProjectWatchInfo> watchedProjects) {
                    cbox.setEnabled(true);
                  }

                  @Override
                  public void onFailure(Throwable caught) {
                    cbox.setEnabled(true);
                    info.notify(type, oldVal);
                    cbox.setValue(oldVal);
                    super.onFailure(caught);
                  }
                });
          }
        });

    cbox.setValue(info.notify(type));
    table.setWidget(row, col, cbox);
  }
}
