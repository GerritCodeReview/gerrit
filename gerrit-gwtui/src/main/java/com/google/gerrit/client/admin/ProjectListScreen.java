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
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.client.ui.NavigationTable;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.SmallHeading;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.VisibleProjectsInfo;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.FlexTable.FlexCellFormatter;
import com.google.gwt.user.client.ui.HTMLTable.Cell;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

import java.util.List;

public class ProjectListScreen extends Screen {
  private ProjectTable projects;

  private NpTextBox createProjectTxt;
  private Button createProjectNew;
  private NpTextBox parentNameBox;
  private SuggestBox parentTxt;
  private CheckBox showParentCandidates;
  private ListBox suggestedParents;
  private boolean isParentCandidatesPopulated;

  private final static String NO_PARENT_SELECTED = "--";

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.visibleProjects(new ScreenLoadCallback<VisibleProjectsInfo>(this) {
      @Override
      protected void preDisplay(final VisibleProjectsInfo result) {
        projects.display(result.getVisibleProjectsList());

        if (result.userCanCreateProject()) {
          final VerticalPanel fp = new VerticalPanel();
          fp.setStyleName(Gerrit.RESOURCES.css().addSshKeyPanel());
          fp.add(new SmallHeading(Util.C.headingCreateProject()));

          createProjectTxt = new NpTextBox();
          createProjectTxt.setVisibleLength(50);
          createProjectTxt.addKeyPressHandler(new KeyPressHandler() {
            @Override
            public void onKeyPress(KeyPressEvent event) {
              if (event.getCharCode() == KeyCodes.KEY_ENTER) {
                doCreateProject();
              }
            }
          });

          createProjectNew = new Button(Util.C.buttonCreateProject());
          createProjectNew.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(final ClickEvent event) {
              doCreateProject();
            }
          });

          parentNameBox = new NpTextBox();
          parentTxt = new SuggestBox(new ProjectNameSuggestOracle(), parentNameBox);
          parentNameBox.setVisibleLength(50);
          parentNameBox.addStyleName(Gerrit.RESOURCES.css().inputFieldTypeHint());

          showParentCandidates = new CheckBox(Util.C.parentCandidates());
          showParentCandidates.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              parentTxt.setText("");

              if (!isParentCandidatesPopulated) {
                Util.PROJECT_SVC.suggestParentCandidates(new AsyncCallback<List<NameKey>>() {

                  @Override
                  public void onSuccess(List<NameKey> result) {
                    isParentCandidatesPopulated = true;
                    for(NameKey projectName: result) {
                      suggestedParents.addItem(projectName.get());
                    }
                  }

                  @Override
                  public void onFailure(Throwable caught) {
                  }
                });
              }

              suggestedParents.setEnabled(!suggestedParents.isEnabled());
              parentNameBox.setEnabled(!parentNameBox.isEnabled());
            }
          });

          suggestedParents = new ListBox();
          suggestedParents.addItem(NO_PARENT_SELECTED);
          suggestedParents.setEnabled(false);

          final Grid grid = new Grid(3, 2);
          grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
          grid.setText(0, 0, Util.C.columnProjectName() + ":");
          grid.setWidget(0, 1, createProjectTxt);
          grid.setText(1, 0, Util.C.headingParentProjectName() + ":");
          grid.setWidget(1, 1, parentTxt);

          grid.setWidget(2, 0, showParentCandidates);
          grid.setWidget(2, 1, suggestedParents);


          fp.add(grid);
          fp.add(createProjectNew);

          add(fp);
        }

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

  private void doCreateProject() {
    // return if project or parent name was not provided
    //

    final String projectName = createProjectTxt.getText();
    String parentName = null;

    if (showParentCandidates.getValue()) {
      parentName = suggestedParents.getItemText(suggestedParents.getSelectedIndex());
    } else {
      parentName =  parentTxt.getText();
    }

    if (projectName.isEmpty() || parentName.equals(NO_PARENT_SELECTED)) {
      return;
    }

    Util.PROJECT_SVC.createNewProject(projectName, parentName,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            History.newItem(Dispatcher.toProjectAdmin(new Project.NameKey(projectName),
                ProjectScreen.INFO));
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
          }
        });
  }
}
