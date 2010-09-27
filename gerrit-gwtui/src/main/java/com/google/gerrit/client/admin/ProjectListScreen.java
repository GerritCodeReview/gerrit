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

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.RadioButton;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;

import java.util.List;

public class ProjectListScreen extends Screen {
  private ProjectTable projects;
  private Button deleteButton;

  @Override
  protected void onLoad() {
    super.onLoad();
    loadProjects();
  }

  protected void loadProjects() {
    Util.PROJECT_SVC.visibleProjects(new ScreenLoadCallback<List<ProjectData>>(this) {
      @Override
      protected void preDisplay(final List<ProjectData> result) {
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

    /* Add delete button */
    deleteButton = new Button(Util.C.buttonDeleteBranch());

    deleteButton.setEnabled(false);
    deleteButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        final Project.NameKey projectName = projects.getSelectedProjectName();
        final String message = buildDeleteMessage(projectName);
        final ConfirmationDialog confirmationDialog =
            new ConfirmationDialog(Gerrit.C.projectDeletionDialogTitle(),
                new HTML(message), new ConfirmationCallback() {
                  @Override
                  public void onOk() {
                    Util.PROJECT_SVC.deleteProject(projectName,
                        new GerritCallback<Boolean>() {
                          @Override
                          public void onSuccess(Boolean result) {
                            if (result) {
                              projects.removeProject(projectName.get());
                              deleteButton.setEnabled(false);
                            }
                          }
                        });
                  }
                });
        confirmationDialog.center();
      }
    });

    add(deleteButton);

    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
    fp.add(new SmallHeading(Util.C.headingCreateGroup()));
  }

  private String buildDeleteMessage(Project.NameKey projectName) {
    final StringBuilder message = new StringBuilder();
    message.append("<b>").append(Gerrit.C.projectDeletionConfirmationMessage());
    message.append("</b>");
    message.append("<p>");
    if (projectName != null) {
      message.append(projectName);
    }
    message.append("</p>");
    return message.toString();
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    projects.setRegisterKeys(true);
  }

  private class ProjectTable extends NavigationTable<ProjectData> {
    ProjectTable() {
      setSavePointerId(PageLinks.ADMIN_PROJECTS);
      keysNavigation.add(new PrevKeyCommand(0, 'k', Util.C.projectListPrev()));
      keysNavigation.add(new NextKeyCommand(0, 'j', Util.C.projectListNext()));
      keysNavigation.add(new OpenKeyCommand(0, 'o', Util.C.projectListOpen()));
      keysNavigation.add(new OpenKeyCommand(0, KeyCodes.KEY_ENTER, Util.C
          .projectListOpen()));

      table.setText(0, 2, Util.C.columnProjectName());
      table.setText(0, 3, Util.C.columnProjectDescription());
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
      fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().iconHeader());
      fmt.addStyleName(0, 2, Gerrit.RESOURCES.css().dataHeader());
      fmt.addStyleName(0, 3, Gerrit.RESOURCES.css().dataHeader());
    }

    @Override
    protected Object getRowItemKey(final ProjectData item) {
      return item.getNameKey();
    }

    @Override
    protected void onOpenRow(final int row) {
      History.newItem(link(getRowItem(row)));
    }

    private String link(final ProjectData item) {
      return Dispatcher.toProjectAdmin(item.getNameKey(), ProjectScreen.INFO);
    }

    void display(final List<ProjectData> result) {
      while (1 < table.getRowCount())
        table.removeRow(table.getRowCount() - 1);

      for (final ProjectData k : result) {
        final int row = table.getRowCount();
        table.insertRow(row);
        applyDataRowStyle(row);
        populate(row, k);
      }
    }

    void populate(final int row, final ProjectData k) {
      /* Add a radioButton column for each row, except for "All projects" row */
      if (!k.getNameKey().equals(Gerrit.getConfig().getWildProject())) {
        RadioButton rb = new RadioButton("");
        rb.addValueChangeHandler(new ValueChangeHandler<Boolean>() {
          @Override
          public void onValueChange(ValueChangeEvent<Boolean> event) {
            final boolean selected = event.getValue();
            deleteButton.setEnabled(selected && k.canBeDeleted());
          }
        });
        table.setWidget(row, 1, rb);
      }

      table.setWidget(row, 2, new Hyperlink(k.getName(), link(k)));
      table.setText(row, 3, k.getDescription());

      final FlexCellFormatter fmt = table.getFlexCellFormatter();
      fmt.addStyleName(row, 1, Gerrit.RESOURCES.css().iconCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().dataCell());
      fmt.addStyleName(row, 2, Gerrit.RESOURCES.css().cPROJECT());
      fmt.addStyleName(row, 3, Gerrit.RESOURCES.css().dataCell());

      setRowItem(row, k);
    }

    public Project.NameKey getSelectedProjectName() {
      Project.NameKey selectedProject = null;
      for (int row = 1; row < table.getRowCount(); row++) {
        final ProjectData k = getRowItem(row);
        final Widget widget = table.getWidget(row, 1);
        if (k != null && widget instanceof RadioButton
            && ((RadioButton) widget).getValue()) {
          selectedProject = k.getNameKey();
          break;
        }
      }
      return selectedProject;
    }

    public void removeProject(final String projectToDelete) {
      for (int row = 1; row < table.getRowCount(); row++) {
        final ProjectData k = getRowItem(row);
        if (k != null && k.getName().equals(projectToDelete)) {
          table.removeRow(row);
          break;
        }
      }
    }
  }
}
