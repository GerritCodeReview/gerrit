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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwtexpui.globalkey.client.KeyCommand;

public class ProjectsSelectionTable extends ProjectsTable {

  private ValueChangeListener valueChangeListener;

  public ProjectsSelectionTable() {
    super(true);
    keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
    keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
    keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
    keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
        .projectListOpen()));
    keysNavigation.add(new SpaceSelectedCommand(0, ' ', "Select Project"));

    table.setText(0, 2, Util.C.projectName());
    table.setText(0, 3, Util.C.projectDescription());

    final FlexCellFormatter fmt = table.getFlexCellFormatter();
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
    fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
    fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
  }

  @Override
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
  protected void onOpenRow(final int row) {
    History.newItem(link(getRowItem(row)));
  }

  private String link(final ProjectData item) {
    return Dispatcher.toProjectAdmin(item.getNameKey(), ProjectScreen.INFO);
  }

  @Override
  protected void populate(final int row, final ProjectData k) {

    /* Add a checkBox column for each row, except for "All projects" row */
    if (!k.getNameKey().equals(Gerrit.getConfig().getWildProject())
        && k.canBeDeleted()) {
      CheckBox cb = new CheckBox();
      cb.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
        @Override
        public void onValueChange(ValueChangeEvent<Boolean> event) {
          if (valueChangeListener != null) {
            valueChangeListener.onValueChange(event.getValue(), k.getNameKey());
          }
        }
      });
      table.setWidget(row, 1, cb);
    }
    table.setWidget(row, 2, new Hyperlink(k.getName(), link(k)));
    table.setText(row, 3, k.getDescription());

    setRowItem(row, k);
  }

  public Project.NameKey getSelectedProjectName() {
    Project.NameKey selectedProject = null;
    for (int row = 1; row < table.getRowCount(); row++) {
      final ProjectData k = getRowItem(row);
      final Widget widget = table.getWidget(row, 1);
      if (k != null && widget instanceof CheckBox
          && ((CheckBox) widget).getValue()) {
        selectedProject = k.getNameKey();
        break;
      }
    }
    return selectedProject;
  }

  public void removeProject(final NameKey project) {
    for (int row = 1; row < table.getRowCount(); row++) {
      final ProjectData k = getRowItem(row);
      if (k != null && k.getNameKey().equals(project)) {
        table.removeRow(row);
        break;
      }
    }
  }

  public void setValueChangeListener(ValueChangeListener valueChangeListener) {
    this.valueChangeListener = valueChangeListener;
  }

  public void setCheckBoxFocus(int row) {
    final CheckBox checkBox = (CheckBox) table.getWidget(row, 1);
    checkBox.setFocus(true);
  }

  class SpaceSelectedCommand extends KeyCommand {
    public SpaceSelectedCommand(int mask, char key, String help) {
      super(mask, key, help);
    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
      final int i = getCurrentRow();
      final CheckBox checkBox = (CheckBox) table.getWidget(i, 1);
      boolean value = checkBox.getValue();
      if (value) {
        checkBox.setValue(false, true);
      } else {
        checkBox.setValue(true, true);
      }
    }
  }

  /** Listener to checkBox onValueChange event. */
  public static interface ValueChangeListener {
    public void onValueChange(boolean value, NameKey selectedProject);
  }
}
