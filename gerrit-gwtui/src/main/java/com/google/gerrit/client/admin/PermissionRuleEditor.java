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

import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.ValueListBox;

import java.io.IOException;
import java.util.Arrays;

public class PermissionRuleEditor extends Composite implements
    Editor<PermissionRule> {
  interface Binder extends UiBinder<HTMLPanel, PermissionRuleEditor> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  ValueListBox<PermissionRule.Action> action;

  @UiField
  CheckBox force;

  @UiField
  @Path("group.name")
  InlineLabel normalGroupName;

  @UiField
  @Path("group.name")
  InlineLabel deletedGroupName;

  @UiField
  DivElement normal;
  @UiField
  DivElement deleted;

  private boolean isDeleted;

  public PermissionRuleEditor(Permission permission) {
    action = new ValueListBox<PermissionRule.Action>(actionRenderer);
    action.setValue(PermissionRule.Action.ALLOW);
    action.setAcceptableValues(Arrays.asList(PermissionRule.Action.values()));

    initWidget(uiBinder.createAndBindUi(this));

    force.setVisible(Permission.PUSH.equals(permission.getName())
        || Permission.PUSH_TAG.equals(permission.getName()));
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
