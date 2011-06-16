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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccessSection;
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
  DivElement editTools;

  @UiField
  Button edit;

  @UiField
  Button cancel1;

  @UiField
  Button cancel2;

  @UiField
  ProjectAccessEditor accessEditor;

  @UiField
  DivElement commitTools;

  @UiField
  NpTextArea commitMessage;

  @UiField
  Button commit;

  private Driver driver;

  private ProjectAccess access;

  public ProjectAccessScreen(final Project.NameKey toShow) {
    super(toShow);
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    add(uiBinder.createAndBindUi(this));

    driver = GWT.create(Driver.class);
    accessEditor.setEditing(false);
    driver.initialize(accessEditor);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.PROJECT_SVC.projectAccess(getProjectKey(),
        new ScreenLoadCallback<ProjectAccess>(this) {
          @Override
          public void preDisplay(ProjectAccess access) {
            displayReadOnly(access);
          }
        });
  }

  private void displayReadOnly(ProjectAccess access) {
    this.access = access;
    accessEditor.setEditing(false);
    UIObject.setVisible(editTools, !access.getOwnerOf().isEmpty());
    edit.setEnabled(!access.getOwnerOf().isEmpty());
    cancel1.setVisible(false);
    UIObject.setVisible(commitTools, false);
    driver.edit(access);
  }

  @UiHandler("edit")
  void onEdit(ClickEvent event) {
    edit.setEnabled(false);
    cancel1.setVisible(true);
    UIObject.setVisible(commitTools, true);
    accessEditor.setEditing(true);
    driver.edit(access);
  }

  @UiHandler(value={"cancel1", "cancel2"})
  void onCancel(ClickEvent event) {
    Gerrit.display(PageLinks.toProjectAcceess(getProjectKey()));
  }

  @UiHandler("commit")
  void onCommit(ClickEvent event) {
    ProjectAccess access = driver.flush();

    if (driver.hasErrors()) {
      Window.alert(Util.C.errorsMustBeFixed());
      return;
    }

    String message = commitMessage.getText().trim();
    if ("".equals(message)) {
      message = null;
    }

    enable(false);
    Util.PROJECT_SVC.changeProjectAccess( //
        getProjectKey(), //
        access.getRevision(), //
        message, //
        access.getLocal(), //
        new GerritCallback<ProjectAccess>() {
          @Override
          public void onSuccess(ProjectAccess access) {
            enable(true);
            commitMessage.setText("");
            displayReadOnly(access);
          }

          @Override
          public void onFailure(Throwable caught) {
            enable(true);
            super.onFailure(caught);
          }
        });
  }

  private void enable(boolean enabled) {
    commitMessage.setEnabled(enabled);
    commit.setEnabled(enabled);
    cancel1.setEnabled(enabled);
    cancel2.setEnabled(enabled);
  }
}
