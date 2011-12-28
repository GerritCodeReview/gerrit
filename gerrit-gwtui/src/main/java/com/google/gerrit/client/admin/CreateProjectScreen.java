// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.HintTextBox;
import com.google.gerrit.client.ui.ProjectNameSuggestOracle;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

public class CreateProjectScreen extends Screen {
  private NpTextBox project;
  private Button create;
  private HintTextBox parent;
  private SuggestBox sugestParent;
  private CheckBox emptyCommit;
  private CheckBox permissionsOnly;

  @Override
  protected void onLoad() {
    super.onLoad();
    display();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.createProjectTitle());

    addCreateProjectPanel();
  }

  private void addCreateProjectPanel() {
    final VerticalPanel fp = new VerticalPanel();
    fp.setStyleName(Gerrit.RESOURCES.css().createProjectPanel());

    initCreateTxt();
    initCreateButton();
    initParentBox();

    addGrid(fp);

    emptyCommit = new CheckBox(Util.C.checkBoxEmptyCommit());
    permissionsOnly = new CheckBox(Util.C.checkBoxPermissionsOnly());
    fp.add(emptyCommit);
    fp.add(permissionsOnly);
    fp.add(create);
    add(fp);
  }

  private void initCreateTxt() {
    project = new NpTextBox();
    project.setVisibleLength(50);
    project.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doCreateProject();
        }
      }
    });
  }

  private void initCreateButton() {
    create = new Button(Util.C.buttonCreateProject());
    create.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doCreateProject();
      }
    });
  }

  private void initParentBox() {
    parent = new HintTextBox();
    sugestParent =
        new SuggestBox(new ProjectNameSuggestOracle(), parent);
    parent.setVisibleLength(50);
  }

  private void addGrid(final VerticalPanel fp) {
    final Grid grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.columnProjectName() + ":");
    grid.setWidget(0, 1, project);
    grid.setText(1, 0, Util.C.headingParentProjectName() + ":");
    grid.setWidget(1, 1, sugestParent);

    fp.add(grid);
  }

  private void doCreateProject() {
    final String projectName = project.getText().trim();
    final String parentName = sugestParent.getText().trim();

    if ("".equals(projectName)) {
      project.setFocus(true);
      return;
    }

    enableForm(false);
    Util.PROJECT_SVC.createNewProject(projectName, parentName,
        emptyCommit.getValue(), permissionsOnly.getValue(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            History.newItem(Dispatcher.toProjectAdmin(new Project.NameKey(
                projectName), ProjectScreen.INFO));
          }

          @Override
          public void onFailure(Throwable caught) {
            new ErrorDialog(caught.getMessage()) {
              @Override
              public void setText(final String t) {
              }
            }.center();
            enableForm(true);
          }
        });
  }

  private void enableForm(final boolean enabled) {
    project.setEnabled(enabled);
    create.setEnabled(enabled);
    parent.setEnabled(enabled);
    emptyCommit.setEnabled(enabled);
    permissionsOnly.setEnabled(enabled);
  }
}
