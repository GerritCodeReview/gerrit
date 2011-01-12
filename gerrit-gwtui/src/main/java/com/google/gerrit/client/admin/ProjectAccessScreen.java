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

import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.editor.client.SimpleBeanEditorDriver;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwtexpui.globalkey.client.NpTextArea;

public class ProjectAccessScreen extends ProjectScreen {
  interface Binder extends UiBinder<HTMLPanel, ProjectAccessScreen> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  interface Driver extends SimpleBeanEditorDriver< //
      ProjectAccess, //
      ProjectAccessEditor> {
  }

  @UiField
  ProjectAccessEditor accessEditor;

  @UiField
  DivElement commitTools;

  @UiField
  NpTextArea commitMessage;

  @UiField
  Button save;

  private Driver driver;

  public ProjectAccessScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    add(uiBinder.createAndBindUi(this));

    driver = GWT.create(Driver.class);
    driver.initialize(accessEditor);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectAccess(getProjectKey(),
        new ScreenLoadCallback<ProjectAccess>(this) {
          @Override
          public void preDisplay(ProjectAccess access) {
            edit(access);
          }
        });
  }

  void edit(ProjectAccess access) {
    driver.edit(access);
    UIObject.setVisible(commitTools, !access.getOwnerOf().isEmpty());
  }

  @UiHandler("save")
  void onSave(ClickEvent event) {
    ProjectAccess access = driver.flush();

    if (driver.hasErrors()) {
      Window.alert("There are errors, please fix them");
      return;
    }

    String message = commitMessage.getText().trim();
    if ("".equals(message)) {
      message = null;
    }

    Util.PROJECT_SVC.changeProjectAccess( //
        getProjectKey(), //
        access.getRevision(), //
        message, //
        access.getLocal(), //
        new GerritCallback<ProjectAccess>() {
          @Override
          public void onSuccess(ProjectAccess result) {
            commitMessage.setText("");
            driver.edit(result);
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
          }
        });
  }
}
