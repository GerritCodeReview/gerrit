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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.ConfirmationCallback;
import com.google.gerrit.client.ConfirmationDialog;
import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwtexpui.globalkey.client.NpTextBox;

public class ProjectAdminScreen extends ProjectScreen {

  private NpTextBox projectNameTxtBox;
  private Button renameButton;

  public ProjectAdminScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectDetail(getProjectKey(), new ScreenLoadCallback<ProjectDetail>(this) {
      @Override
      public void preDisplay(final ProjectDetail result) {
        display(result);
      }
    });
  }

  private void display(final ProjectDetail projectDetails) {
    projectNameTxtBox.setReadOnly(!projectDetails.canRename);
    renameButton.setVisible(projectDetails.canRename);
    renameButton.setEnabled(false);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    final FlowPanel panel = new FlowPanel();
    panel.setStyleName(Gerrit.RESOURCES.css().renameProjectPanel());

    final Grid grid = new Grid(1, 2);

    projectNameTxtBox = new NpTextBox();
    projectNameTxtBox.setVisibleLength(50);
    projectNameTxtBox.setText(getProjectKey().toString());
    projectNameTxtBox.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER) {
          doChangeProjectName();
        }
      }
    });
    projectNameTxtBox.addKeyUpHandler(new KeyUpHandler() {
      @Override
      public void onKeyUp(KeyUpEvent event) {
        final String newProjectName = projectNameTxtBox.getText();
        renameButton.setEnabled(!getProjectKey().toString().equals(newProjectName));
      }
    });

    grid.setText(0, 0, Util.C.columnProjectName() + ":");
    grid.setWidget(0, 1, projectNameTxtBox);

    renameButton = new Button(Util.C.buttonRenameProject());
    renameButton.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(final ClickEvent event) {
        doChangeProjectName();
      }
    });
    renameButton.addKeyPressHandler(new KeyPressHandler() {
      @Override
      public void onKeyPress(KeyPressEvent event) {
        if (event.getCharCode() == KeyCodes.KEY_ENTER || event.getCharCode() == ' ') {
          doChangeProjectName();
        }
      }
    });

    panel.add(grid);
    panel.add(renameButton);

    add(panel);
  }

  private void doChangeProjectName() {
    final String newProjectName = projectNameTxtBox.getText();
    if (getProjectKey().toString().equals(newProjectName)) {
      return;
    }

    StringBuilder message = new StringBuilder();
    message.append("<p><b>").append(Gerrit.M.projectRenameConfirmationMessage(getProjectKey().toString(), newProjectName)).append("</b></p>");
    ConfirmationDialog confirmationDialog =
      new ConfirmationDialog(Gerrit.C.projectRenameDialogTitle(),
          new HTML(message.toString()), new ConfirmationCallback() {
        @Override
        public void onOk() {
          projectNameTxtBox.setEnabled(false);
          renameButton.setEnabled(false);
          Util.PROJECT_SVC.renameProject(getProjectKey(), newProjectName,
              new GerritCallback<ProjectDetail>() {
                public void onSuccess(final ProjectDetail result) {
                  Gerrit.display(Dispatcher.toProjectAdmin(new Project.NameKey(newProjectName), ADMIN));
                }

                @Override
                public void onFailure(final Throwable caught) {
                  projectNameTxtBox.setEnabled(true);
                  renameButton.setEnabled(true);
                  super.onFailure(caught);
                }
              });
        }
    });
    confirmationDialog.center();
  }
}
