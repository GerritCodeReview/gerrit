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

import static com.google.gerrit.common.data.Permission.EDIT_TOPIC_NAME;
import static com.google.gerrit.common.data.Permission.PUSH;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.ValueListBox;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class PermissionRuleEditor extends Composite
    implements Editor<PermissionRule>, ValueAwareEditor<PermissionRule> {
  interface Binder extends UiBinder<HTMLPanel, PermissionRuleEditor> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  ValueListBox<PermissionRule.Action> action;

  @UiField(provided = true)
  RangeBox min;

  @UiField(provided = true)
  RangeBox max;

  @UiField CheckBox force;

  @UiField Anchor groupNameLink;
  @UiField SpanElement groupNameSpan;
  @UiField SpanElement deletedGroupName;

  @UiField Anchor deleteRule;

  @UiField DivElement normal;
  @UiField DivElement deleted;

  @UiField SpanElement rangeEditor;

  private Map<AccountGroup.UUID, GroupInfo> groupInfo;
  private boolean isDeleted;
  private HandlerRegistration clickHandler;

  public PermissionRuleEditor(
      boolean readOnly,
      Map<AccountGroup.UUID, GroupInfo> groupInfo,
      AccessSection section,
      Permission permission,
      PermissionRange.WithDefaults validRange) {
    this.groupInfo = groupInfo;
    action = new ValueListBox<>(actionRenderer);

    if (validRange != null && 10 < validRange.getRangeSize()) {
      min = new RangeBox.Box();
      max = new RangeBox.Box();

    } else if (validRange != null) {
      RangeBox.List minList = new RangeBox.List();
      RangeBox.List maxList = new RangeBox.List();
      List<Integer> valueList = validRange.getValuesAsList();

      minList.list.setValue(validRange.getMin());
      maxList.list.setValue(validRange.getMax());

      minList.list.setAcceptableValues(valueList);
      maxList.list.setAcceptableValues(valueList);

      min = minList;
      max = maxList;

    } else {
      min = new RangeBox.Box();
      max = new RangeBox.Box();

      if (GlobalCapability.PRIORITY.equals(permission.getName())) {
        action.setValue(PermissionRule.Action.INTERACTIVE);
        action.setAcceptableValues(
            Arrays.asList(PermissionRule.Action.INTERACTIVE, PermissionRule.Action.BATCH));

      } else {
        action.setValue(PermissionRule.Action.ALLOW);
        action.setAcceptableValues(
            Arrays.asList(
                PermissionRule.Action.ALLOW,
                PermissionRule.Action.DENY,
                PermissionRule.Action.BLOCK));
      }
    }

    initWidget(uiBinder.createAndBindUi(this));

    String name = permission.getName();
    boolean canForce = PUSH.equals(name);
    if (canForce) {
      String ref = section.getName();
      canForce = !ref.startsWith("refs/for/") && !ref.startsWith("^refs/for/");
      force.setText(PermissionRule.FORCE_PUSH);
    } else {
      canForce = EDIT_TOPIC_NAME.equals(name);
      force.setText(PermissionRule.FORCE_EDIT);
    }
    force.setVisible(canForce);
    force.setEnabled(!readOnly);

    if (validRange != null) {
      min.setEnabled(!readOnly);
      max.setEnabled(!readOnly);
      action.getElement().getStyle().setDisplay(Display.NONE);

    } else {
      rangeEditor.getStyle().setDisplay(Display.NONE);
      action.getElement().setPropertyBoolean("disabled", readOnly);
    }

    if (readOnly) {
      deleteRule.removeFromParent();
      deleteRule = null;
    }

    if (name.equals(GlobalCapability.BATCH_CHANGES_LIMIT)) {
      min.setEnabled(false);
    }
  }

  boolean isDeleted() {
    return isDeleted;
  }

  @UiHandler("deleteRule")
  void onDeleteRule(@SuppressWarnings("unused") ClickEvent event) {
    isDeleted = true;
    normal.getStyle().setDisplay(Display.NONE);
    deleted.getStyle().setDisplay(Display.BLOCK);
  }

  @UiHandler("undoDelete")
  void onUndoDelete(@SuppressWarnings("unused") ClickEvent event) {
    isDeleted = false;
    deleted.getStyle().setDisplay(Display.NONE);
    normal.getStyle().setDisplay(Display.BLOCK);
  }

  @Override
  public void setValue(PermissionRule value) {
    if (clickHandler != null) {
      clickHandler.removeHandler();
      clickHandler = null;
    }

    GroupReference ref = value.getGroup();
    GroupInfo info =
        groupInfo != null && ref.getUUID() != null ? groupInfo.get(ref.getUUID()) : null;

    boolean link;
    if (ref.getUUID() != null && AccountGroup.isInternalGroup(ref.getUUID())) {
      final String token = Dispatcher.toGroup(ref.getUUID());
      groupNameLink.setText(ref.getName());
      groupNameLink.setHref("#" + token);
      groupNameLink.setTitle(info != null ? info.getDescription() : null);
      groupNameLink.setTarget(null);
      clickHandler =
          groupNameLink.addClickHandler(
              new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                  event.preventDefault();
                  event.stopPropagation();
                  Gerrit.display(token);
                }
              });
      link = true;
    } else if (info != null && info.getUrl() != null) {
      groupNameLink.setText(ref.getName());
      groupNameLink.setHref(info.getUrl());
      groupNameLink.setTitle(info.getDescription());
      groupNameLink.setTarget("_blank");
      link = true;
    } else {
      groupNameSpan.setInnerText(ref.getName());
      groupNameSpan.setTitle(ref.getUUID() != null ? ref.getUUID().get() : "");
      link = false;
    }

    deletedGroupName.setInnerText(ref.getName());
    groupNameLink.setVisible(link);
    UIObject.setVisible(groupNameSpan, !link);
  }

  @Override
  public void setDelegate(EditorDelegate<PermissionRule> delegate) {}

  @Override
  public void flush() {}

  @Override
  public void onPropertyChange(String... paths) {}

  private static class ActionRenderer implements Renderer<PermissionRule.Action> {
    @Override
    public String render(PermissionRule.Action object) {
      return object != null ? object.toString() : "";
    }

    @Override
    public void render(PermissionRule.Action object, Appendable appendable) throws IOException {
      appendable.append(render(object));
    }
  }

  private static final ActionRenderer actionRenderer = new ActionRenderer();
}
