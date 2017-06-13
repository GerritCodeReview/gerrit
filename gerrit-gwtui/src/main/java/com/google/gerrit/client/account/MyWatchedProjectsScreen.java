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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectListPopup;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.RemoteSuggestBox;
import com.google.gerrit.common.PageLinks;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.HorizontalPanel;

public class MyWatchedProjectsScreen extends SettingsScreen {
  private Button addNew;
  private RemoteSuggestBox nameBox;
  private HintTextBox filterTxt;
  private MyWatchesTable watchesTab;
  private Button browse;
  private Button delSel;
  private Grid grid;
  private ProjectListPopup projectsPopup;

  @Override
  protected void onInitUI() {
    super.onInitUI();
    createWidgets();

    /* top table */
    grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.watchedProjectName());
    final HorizontalPanel hp = new HorizontalPanel();
    hp.add(nameBox);
    hp.add(browse);
    grid.setWidget(0, 1, hp);

    grid.setText(1, 0, Util.C.watchedProjectFilter());
    grid.setWidget(1, 1, filterTxt);

    final CellFormatter fmt = grid.getCellFormatter();
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 1, Gerrit.RESOURCES.css().topmost());
    fmt.addStyleName(0, 0, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().header());
    fmt.addStyleName(1, 0, Gerrit.RESOURCES.css().bottomheader());

    final FlowPanel fp = new FlowPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().addWatchPanel());
    fp.add(grid);
    fp.add(addNew);
    add(fp);

    /* bottom table */
    add(watchesTab);
    add(delSel);

    /* popup */
    projectsPopup =
        new ProjectListPopup() {
          @Override
          protected void onMovePointerTo(String projectName) {
            // prevent user input from being overwritten by simply poping up
            if (!projectsPopup.isPoppingUp() || "".equals(nameBox.getText())) {
              nameBox.setText(projectName);
            }
          }

          @Override
          protected void openRow(String projectName) {
            nameBox.setText(projectName);
            doAddNew();
          }
        };
    projectsPopup.initPopup(Util.C.projects(), PageLinks.SETTINGS_PROJECTS);
  }

  protected void createWidgets() {
    nameBox = new RemoteSuggestBox(new ProjectNameSuggestOracle());
    nameBox.setVisibleLength(50);
    nameBox.setHintText(Util.C.defaultProjectName());
    nameBox.addSelectionHandler(
        new SelectionHandler<String>() {
          @Override
          public void onSelection(SelectionEvent<String> event) {
            doAddNew();
          }
        });

    filterTxt = new HintTextBox();
    filterTxt.setVisibleLength(50);
    filterTxt.setHintText(Util.C.defaultFilter());
    filterTxt.addKeyPressHandler(
        new KeyPressHandler() {
          @Override
          public void onKeyPress(KeyPressEvent event) {
            if (event.getNativeEvent().getKeyCode() == KeyCodes.KEY_ENTER) {
              doAddNew();
            }
          }
        });

    addNew = new Button(Util.C.buttonWatchProject());
    addNew.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            doAddNew();
          }
        });

    browse = new Button(Util.C.buttonBrowseProjects());
    browse.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            int top = grid.getAbsoluteTop() - 50; // under page header
            // Try to place it to the right of everything else, but not
            // right justified
            int left =
                5
                    + Math.max(
                        grid.getAbsoluteLeft() + grid.getOffsetWidth(),
                        watchesTab.getAbsoluteLeft() + watchesTab.getOffsetWidth());
            projectsPopup.setPreferredCoordinates(top, left);
            projectsPopup.displayPopup();
          }
        });

    watchesTab = new MyWatchesTable();

    delSel = new Button(Util.C.buttonDeleteSshKey());
    delSel.addClickHandler(
        new ClickHandler() {
          @Override
          public void onClick(ClickEvent event) {
            watchesTab.deleteChecked();
          }
        });
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    populateWatches();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    projectsPopup.closePopup();
  }

  protected void doAddNew() {
    final String projectName = nameBox.getText().trim();
    if ("".equals(projectName)) {
      return;
    }

    String filter = filterTxt.getText();
    if (filter == null || filter.isEmpty() || filter.equals(Util.C.defaultFilter())) {
      filter = null;
    }

    addNew.setEnabled(false);
    nameBox.setEnabled(false);
    filterTxt.setEnabled(false);

    final ProjectWatchInfo projectWatchInfo = JavaScriptObject.createObject().cast();
    projectWatchInfo.project(projectName);
    projectWatchInfo.filter(filterTxt.getText());

    AccountApi.updateWatchedProject(
        "self",
        projectWatchInfo,
        new GerritCallback<JsArray<ProjectWatchInfo>>() {
          @Override
          public void onSuccess(JsArray<ProjectWatchInfo> watchedProjects) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);

            nameBox.setText("");
            watchesTab.insertWatch(projectWatchInfo);
          }

          @Override
          public void onFailure(Throwable caught) {
            addNew.setEnabled(true);
            nameBox.setEnabled(true);
            filterTxt.setEnabled(true);
            super.onFailure(caught);
          }
        });
  }

  protected void populateWatches() {
    AccountApi.getWatchedProjects(
        "self",
        new GerritCallback<JsArray<ProjectWatchInfo>>() {
          @Override
          public void onSuccess(JsArray<ProjectWatchInfo> watchedProjects) {
            watchesTab.display(watchedProjects);
            display();
          }
        });
  }
}
