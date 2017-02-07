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
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.editor.client.adapters.EditorSource;
import com.google.gwt.editor.client.adapters.ListEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ValueListBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccessSectionEditor extends Composite
    implements Editor<AccessSection>, ValueAwareEditor<AccessSection> {
  interface Binder extends UiBinder<HTMLPanel, AccessSectionEditor> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField ValueEditor<String> name;

  @UiField FlowPanel permissionContainer;
  ListEditor<Permission, PermissionEditor> permissions;

  @UiField DivElement addContainer;

  @UiField(provided = true)
  @Editor.Ignore
  ValueListBox<String> permissionSelector;

  @UiField SpanElement deletedName;

  @UiField Anchor deleteSection;

  @UiField DivElement normal;
  @UiField DivElement deleted;

  @UiField SpanElement sectionType;
  @UiField SpanElement sectionName;

  private final ProjectAccess projectAccess;
  private AccessSection value;
  private boolean editing;
  private boolean readOnly;
  private boolean isDeleted;

  public AccessSectionEditor(ProjectAccess access) {
    projectAccess = access;
    permissionSelector = new ValueListBox<>(new PermissionNameRenderer(access.getCapabilities()));
    permissionSelector.addValueChangeHandler(
        new ValueChangeHandler<String>() {
          @Override
          public void onValueChange(ValueChangeEvent<String> event) {
            if (!Util.C.addPermission().equals(event.getValue())) {
              onAddPermission(event.getValue());
            }
          }
        });

    initWidget(uiBinder.createAndBindUi(this));
    permissions = ListEditor.of(new PermissionEditorSource());
  }

  @UiHandler("deleteSection")
  void onDeleteHover(@SuppressWarnings("unused") MouseOverEvent event) {
    normal.addClassName(AdminResources.I.css().deleteSectionHover());
  }

  @UiHandler("deleteSection")
  void onDeleteNonHover(@SuppressWarnings("unused") MouseOutEvent event) {
    normal.removeClassName(AdminResources.I.css().deleteSectionHover());
  }

  @UiHandler("deleteSection")
  void onDeleteSection(@SuppressWarnings("unused") ClickEvent event) {
    isDeleted = true;

    if (name.isVisible() && RefConfigSection.isValid(name.getValue())) {
      deletedName.setInnerText(Util.M.deletedReference(name.getValue()));

    } else {
      String name = Util.C.sectionNames().get(value.getName());
      if (name == null) {
        name = value.getName();
      }
      deletedName.setInnerText(Util.M.deletedSection(name));
    }

    normal.getStyle().setDisplay(Display.NONE);
    deleted.getStyle().setDisplay(Display.BLOCK);
  }

  @UiHandler("undoDelete")
  void onUndoDelete(@SuppressWarnings("unused") ClickEvent event) {
    isDeleted = false;
    deleted.getStyle().setDisplay(Display.NONE);
    normal.getStyle().setDisplay(Display.BLOCK);
  }

  void onAddPermission(String varName) {
    int idx = permissions.getList().size();

    Permission p = value.getPermission(varName, true);
    permissions.getList().add(p);

    PermissionEditor e = permissions.getEditors().get(idx);
    e.beginAddRule();

    rebuildPermissionSelector();
  }

  void editRefPattern() {
    name.edit();
    Scheduler.get()
        .scheduleDeferred(
            new ScheduledCommand() {
              @Override
              public void execute() {
                name.setFocus(true);
              }
            });
  }

  void enableEditing() {
    readOnly = false;
    addContainer.getStyle().setDisplay(Display.BLOCK);
    rebuildPermissionSelector();
  }

  boolean isDeleted() {
    return isDeleted;
  }

  @Override
  public void setValue(AccessSection value) {
    Collections.sort(value.getPermissions());

    this.value = value;
    this.readOnly = !editing || !(projectAccess.isOwnerOf(value) || projectAccess.canUpload());

    name.setEnabled(!readOnly);
    deleteSection.setVisible(!readOnly);

    if (RefConfigSection.isValid(value.getName())) {
      name.setVisible(true);
      name.setIgnoreEditorValue(false);
      sectionType.setInnerText(Util.C.sectionTypeReference());

    } else {
      name.setVisible(false);
      name.setIgnoreEditorValue(true);

      String name = Util.C.sectionNames().get(value.getName());
      if (name != null) {
        sectionType.setInnerText(name);
        sectionName.getStyle().setDisplay(Display.NONE);
      } else {
        sectionType.setInnerText(Util.C.sectionTypeSection());
        sectionName.setInnerText(value.getName());
        sectionName.getStyle().clearDisplay();
      }
    }

    if (readOnly) {
      addContainer.getStyle().setDisplay(Display.NONE);
    } else {
      enableEditing();
    }
  }

  void setEditing(final boolean editing) {
    this.editing = editing;
  }

  private void rebuildPermissionSelector() {
    List<String> perms = new ArrayList<>();

    if (AccessSection.GLOBAL_CAPABILITIES.equals(value.getName())) {
      for (String varName : projectAccess.getCapabilities().keySet()) {
        addPermission(varName, perms);
      }
    } else if (RefConfigSection.isValid(value.getName())) {
      for (LabelType t : projectAccess.getLabelTypes().getLabelTypes()) {
        addPermission(Permission.forLabel(t.getName()), perms);
      }
      for (LabelType t : projectAccess.getLabelTypes().getLabelTypes()) {
        addPermission(Permission.forLabelAs(t.getName()), perms);
      }
      for (String varName : Util.C.permissionNames().keySet()) {
        addPermission(varName, perms);
      }
    }
    if (perms.isEmpty()) {
      addContainer.getStyle().setDisplay(Display.NONE);
    } else {
      addContainer.getStyle().setDisplay(Display.BLOCK);
      perms.add(0, Util.C.addPermission());
      permissionSelector.setValue(Util.C.addPermission());
      permissionSelector.setAcceptableValues(perms);
    }
  }

  private void addPermission(final String permissionName, final List<String> permissionList) {
    if (value.getPermission(permissionName) != null) {
      return;
    }
    if (Gerrit.info().gerrit().isAllProjects(projectAccess.getProjectName())
        && !Permission.canBeOnAllProjects(value.getName(), permissionName)) {
      return;
    }
    permissionList.add(permissionName);
  }

  @Override
  public void flush() {
    List<Permission> src = permissions.getList();
    List<Permission> keep = new ArrayList<>(src.size());

    for (int i = 0; i < src.size(); i++) {
      PermissionEditor e = (PermissionEditor) permissionContainer.getWidget(i);
      if (!e.isDeleted()) {
        keep.add(src.get(i));
      }
    }
    value.setPermissions(keep);
  }

  @Override
  public void onPropertyChange(String... paths) {}

  @Override
  public void setDelegate(EditorDelegate<AccessSection> delegate) {}

  private class PermissionEditorSource extends EditorSource<PermissionEditor> {
    @Override
    public PermissionEditor create(int index) {
      PermissionEditor subEditor =
          new PermissionEditor(projectAccess, readOnly, value, projectAccess.getLabelTypes());
      permissionContainer.insert(subEditor, index);
      return subEditor;
    }

    @Override
    public void dispose(PermissionEditor subEditor) {
      subEditor.removeFromParent();
    }

    @Override
    public void setIndex(PermissionEditor subEditor, int index) {
      permissionContainer.insert(subEditor, index);
    }
  }
}
