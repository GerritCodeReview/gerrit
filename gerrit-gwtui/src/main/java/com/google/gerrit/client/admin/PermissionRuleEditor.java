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

import static com.google.gerrit.common.data.Permission.*;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ValueLabel;
import com.google.gwt.user.client.ui.ValueListBox;

import java.io.IOException;
import java.util.Arrays;

public class PermissionRuleEditor extends Composite implements
    Editor<PermissionRule>, ValueAwareEditor<PermissionRule> {
  interface Binder extends UiBinder<HTMLPanel, PermissionRuleEditor> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  @Path("action")
  ValueLabel<PermissionRule.Action> actionLabel;
  @UiField(provided = true)
  @Path("action")
  ValueListBox<PermissionRule.Action> actionEditor;

  @UiField
  CheckBox force;

  @UiField
  Hyperlink normalGroupName;
  @UiField
  SpanElement deletedGroupName;

  @UiField
  Anchor deleteRule;

  @UiField
  DivElement normal;
  @UiField
  DivElement deleted;

  private boolean isDeleted;

  public PermissionRuleEditor(boolean readOnly, AccessSection section,
      Permission permission) {
    actionLabel = new ValueLabel<PermissionRule.Action>(actionRenderer);
    actionEditor = new ValueListBox<PermissionRule.Action>(actionRenderer);
    actionEditor.setValue(PermissionRule.Action.ALLOW);
    actionEditor.setAcceptableValues(Arrays.asList(PermissionRule.Action.values()));

    initWidget(uiBinder.createAndBindUi(this));

    String name = permission.getName();
    boolean canForce = PUSH.equals(name) || PUSH_TAG.equals(name);
    if (canForce) {
      String ref = section.getRefPattern();
      canForce = !ref.startsWith("refs/for/") && !ref.startsWith("^refs/for/");
    }
    force.setVisible(canForce);

    actionLabel.setVisible(readOnly);
    actionEditor.setVisible(!readOnly);
    force.setEnabled(!readOnly);

    if (readOnly) {
      deleteRule.removeFromParent();
      deleteRule = null;
    }
  }

  boolean isDeleted() {
    return isDeleted;
  }

  @UiHandler("deleteRule")
  void onDeleteRule(ClickEvent event) {
    isDeleted = true;
    normal.getStyle().setDisplay(Display.NONE);
    deleted.getStyle().setDisplay(Display.BLOCK);
  }

  @UiHandler("undoDelete")
  void onUndoDelete(ClickEvent event) {
    isDeleted = false;
    deleted.getStyle().setDisplay(Display.NONE);
    normal.getStyle().setDisplay(Display.BLOCK);
  }

  @Override
  public void setValue(PermissionRule value) {
    GroupReference ref = value.getGroup();
    normalGroupName.setTargetHistoryToken(Dispatcher.toGroup(ref.getUUID()));
    normalGroupName.setText(ref.getName());
    deletedGroupName.setInnerText(ref.getName());
  }

  @Override
  public void setDelegate(EditorDelegate<PermissionRule> delegate) {
  }

  @Override
  public void flush() {
  }

  @Override
  public void onPropertyChange(String... paths) {
  }

  private static class ActionRenderer implements
      Renderer<PermissionRule.Action> {
    @Override
    public String render(Action object) {
      return object != null ? object.toString() : "";
    }

    @Override
    public void render(Action object, Appendable appendable) throws IOException {
      appendable.append(render(object));
    }
  }

  private static final ActionRenderer actionRenderer = new ActionRenderer();
}
