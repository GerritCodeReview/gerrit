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
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextBox;
import com.google.gwtjsonrpc.client.VoidResult;

public class CreateProjectScreen extends Screen {
  private NpTextBox createTxt;
  private Button createButton;
  private HintTextBox parentNameBox;
  private SuggestBox parentTxt;
  private CheckBox emptyCommit;

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
    fp.add(emptyCommit);

    fp.add(createButton);
    add(fp);
  }

  private void initCreateTxt() {
    createTxt = new NpTextBox();
    createTxt.setVisibleLength(50);
    createTxt.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (!createTxt.getText().trim().isEmpty()
            && event.getCharCode() == KeyCodes.KEY_ENTER) {
          doCreateProject();
        }
      }
    });

    // Controls if the "Create Project" should be enabled
    createTxt.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        createButton.setEnabled(!createTxt.getText().trim().isEmpty());
      }
    });
  }

  private void initCreateButton() {
    createButton = new Button(Util.C.buttonCreateProject());
    createButton.setEnabled(false);
    createButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doCreateProject();
      }
    });
  }

  private void initParentBox() {
    parentNameBox = new HintTextBox();
    parentTxt = new SuggestBox(new ProjectNameSuggestOracle(), parentNameBox);
    parentNameBox.setVisibleLength(50);
  }

  private void addGrid(final VerticalPanel fp) {
    final Grid grid = new Grid(2, 2);
    grid.setStyleName(Gerrit.RESOURCES.css().infoBlock());
    grid.setText(0, 0, Util.C.columnProjectName() + ":");
    grid.setWidget(0, 1, createTxt);
    grid.setText(1, 0, Util.C.headingParentProjectName() + ":");
    grid.setWidget(1, 1, parentTxt);

    fp.add(grid);
  }

  private void doCreateProject() {
    final String projectName = createTxt.getText().trim();
    final String parentName = parentTxt.getText();
    enableForm(false);

    Util.PROJECT_SVC.createNewProject(projectName, parentName, emptyCommit
        .getValue(), new GerritCallback<VoidResult>() {
      @Override
      public void onSuccess(VoidResult result) {
        History.newItem(Dispatcher.toProjectAdmin(new Project.NameKey(
            projectName), ProjectScreen.INFO));
      }

      @Override
      public void onFailure(Throwable caught) {
        super.onFailure(caught);
        enableForm(true);
      }
    });
  }

  private void enableForm(final boolean enabled) {
    createTxt.setEnabled(enabled);
    createButton.setEnabled(enabled);
    parentNameBox.setEnabled(enabled);
    emptyCommit.setEnabled(enabled);
  }
}
