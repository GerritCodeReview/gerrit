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

import static com.google.gerrit.common.ProjectAccessUtil.mergeSections;
import static com.google.gerrit.common.ProjectAccessUtil.removeEmptyPermissionsAndSections;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.common.CapabilityInfo;
import com.google.gerrit.client.common.CapabilityMap;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.editor.client.SimpleBeanEditorDriver;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwtexpui.globalkey.client.NpTextArea;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  VerticalPanel error;

  @UiField
  ProjectAccessEditor accessEditor;

  @UiField
  DivElement commitTools;

  @UiField
  NpTextArea commitMessage;

  @UiField
  Button commit;

  @UiField
  Button review;

  private Driver driver;

  private ProjectAccess access;
  private CapabilityMap capabilityMap;

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
    // put asynchronous requests in defined order
    // Capabilities
    CallbackGroup cbs = new CallbackGroup();
    CapabilityMap.capabilities().get(cbs.add(new AsyncCallback<CapabilityMap>() {
          @Override
          public void onSuccess(CapabilityMap result) {
            capabilityMap = result;
          }
          @Override
          public void onFailure(Throwable caught) {
            // Handled by ScreenLoadCallback.onFailure().
          }
        }));
    // rest stuff
    Util.PROJECT_SVC.projectAccess(getProjectKey(),
        cbs.addGwtjsonrpc(new ScreenLoadCallback<ProjectAccess>(this) {
          @Override
          public void preDisplay(ProjectAccess access) {
            displayReadOnly(access);
          }
        }));
    savedPanel = ACCESS;
  }

  private void displayReadOnly(ProjectAccess access) {
    this.access = access;
    Map<String, String> allCapabilities = new HashMap<String, String>();
    for (final CapabilityInfo c : Natives.asList(capabilityMap.values())) {
      allCapabilities.put(c.id(), c.name());
    }
    this.access.setCapabilities(allCapabilities);
    accessEditor.setEditing(false);
    UIObject.setVisible(editTools, !access.getOwnerOf().isEmpty() || access.canUpload());
    edit.setEnabled(!access.getOwnerOf().isEmpty() || access.canUpload());
    cancel1.setVisible(false);
    UIObject.setVisible(commitTools, false);
    driver.edit(access);
  }

  @UiHandler("edit")
  void onEdit(ClickEvent event) {
    resetEditors();

    edit.setEnabled(false);
    cancel1.setVisible(true);
    UIObject.setVisible(commitTools, true);
    commit.setVisible(!access.getOwnerOf().isEmpty());
    review.setVisible(access.canUpload());
    accessEditor.setEditing(true);
    driver.edit(access);
  }

  private void resetEditors() {
    // Push an empty instance through the driver before pushing the real
    // data. This will force GWT to delete and recreate the editors, which
    // is required to build initialize them as editable vs. read-only.
    ProjectAccess mock = new ProjectAccess();
    mock.setProjectName(access.getProjectName());
    mock.setRevision(access.getRevision());
    mock.setLocal(Collections.<AccessSection> emptyList());
    mock.setOwnerOf(Collections.<String> emptySet());
    driver.edit(mock);
  }

  @UiHandler(value={"cancel1", "cancel2"})
  void onCancel(ClickEvent event) {
    Gerrit.display(PageLinks.toProjectAcceess(getProjectKey()));
  }

  @UiHandler("commit")
  void onCommit(ClickEvent event) {
    final ProjectAccess access = driver.flush();

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
          public void onSuccess(ProjectAccess newAccess) {
            enable(true);
            commitMessage.setText("");
            error.clear();
            final Set<String> diffs = getDiffs(access, newAccess);
            if (diffs.isEmpty()) {
              displayReadOnly(newAccess);
            } else {
              error.add(new Label(Gerrit.C.projectAccessError()));
              for (final String diff : diffs) {
                error.add(new Label(diff));
              }
              if (access.canUpload()) {
                error.add(new Label(Gerrit.C.projectAccessProposeForReviewHint()));
              }
            }
          }

          private Set<String> getDiffs(ProjectAccess wantedAccess,
              ProjectAccess newAccess) {
            final List<AccessSection> wantedSections =
                mergeSections(removeEmptyPermissionsAndSections(wantedAccess.getLocal()));
            final HashSet<AccessSection> same =
                new HashSet<AccessSection>(wantedSections);
            final HashSet<AccessSection> different =
                new HashSet<AccessSection>(wantedSections.size()
                    + newAccess.getLocal().size());
            different.addAll(wantedSections);
            different.addAll(newAccess.getLocal());
            same.retainAll(newAccess.getLocal());
            different.removeAll(same);

            final Set<String> differentNames = new HashSet<String>();
            for (final AccessSection s : different) {
              differentNames.add(s.getName());
            }
            return differentNames;
          }

          @Override
          public void onFailure(Throwable caught) {
            error.clear();
            enable(true);
            super.onFailure(caught);
          }
        });
  }

  @UiHandler("review")
  void onReview(ClickEvent event) {
    final ProjectAccess access = driver.flush();

    if (driver.hasErrors()) {
      Window.alert(Util.C.errorsMustBeFixed());
      return;
    }

    String message = commitMessage.getText().trim();
    if ("".equals(message)) {
      message = null;
    }

    enable(false);
    Util.PROJECT_SVC.reviewProjectAccess( //
        getProjectKey(), //
        access.getRevision(), //
        message, //
        access.getLocal(), //
        new GerritCallback<Change.Id>() {
          @Override
          public void onSuccess(Change.Id changeId) {
            enable(true);
            commitMessage.setText("");
            error.clear();
            if (changeId != null) {
              Gerrit.display(PageLinks.toChange(changeId));
            } else {
              displayReadOnly(access);
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            error.clear();
            enable(true);
            super.onFailure(caught);
          }
        });
  }

  private void enable(boolean enabled) {
    commitMessage.setEnabled(enabled);
    commit.setEnabled(enabled ? !access.getOwnerOf().isEmpty() : false);
    review.setEnabled(enabled ? access.canUpload() : false);
    cancel1.setEnabled(enabled);
    cancel2.setEnabled(enabled);
  }
}
