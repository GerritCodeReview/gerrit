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
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.editor.client.adapters.EditorSource;
import com.google.gwt.editor.client.adapters.ListEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.text.shared.Renderer;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ValueLabel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PermissionEditor extends Composite implements Editor<Permission>,
    ValueAwareEditor<Permission> {
  interface Binder extends UiBinder<HTMLPanel, PermissionEditor> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  @Path("name")
  ValueLabel<String> normalName;

  @UiField(provided = true)
  @Path("name")
  ValueLabel<String> deletedName;

  @UiField
  CheckBox inherit;

  @UiField
  FlowPanel ruleContainer;
  ListEditor<PermissionRule, PermissionRuleEditor> rules;

  @UiField
  DivElement normal;
  @UiField
  DivElement deleted;

  private Permission value;
  private boolean isDeleted;

  public PermissionEditor() {
    normalName = new ValueLabel<String>(nameRenderer);
    deletedName = new ValueLabel<String>(nameRenderer);

    initWidget(uiBinder.createAndBindUi(this));
    rules = ListEditor.of(new RuleEditorSource());
  }

  @UiHandler("deletePermission")
  void onDeletePermission(ClickEvent event) {
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

  boolean isDeleted() {
    return isDeleted;
  }

  @Override
  public void setValue(Permission value) {
    this.value = value;

    if (value != null && Permission.OWNER.equals(value.getName())) {
      inherit.setEnabled(false);
    } else {
      inherit.setEnabled(true);
    }
  }

  @Override
  public void flush() {
    List<PermissionRule> src = rules.getList();
    List<PermissionRule> keep = new ArrayList<PermissionRule>(src.size());

    for (int i = 0; i < src.size(); i++) {
      PermissionRuleEditor e =
          (PermissionRuleEditor) ruleContainer.getWidget(i);
      if (!e.isDeleted()) {
        keep.add(src.get(i));
      }
    }
    value.setRules(keep);
  }

  @Override
  public void onPropertyChange(String... paths) {
  }

  @Override
  public void setDelegate(EditorDelegate<Permission> delegate) {
  }

  private class RuleEditorSource extends EditorSource<PermissionRuleEditor> {
    @Override
    public PermissionRuleEditor create(int index) {
      PermissionRuleEditor subEditor = new PermissionRuleEditor(value);
      ruleContainer.insert(subEditor, index);
      return subEditor;
    }

    @Override
    public void dispose(PermissionRuleEditor subEditor) {
      subEditor.removeFromParent();
    }

    @Override
    public void setIndex(PermissionRuleEditor subEditor, int index) {
      ruleContainer.insert(subEditor, index);
    }
  }

  private static final NameRenderer nameRenderer = new NameRenderer();

  private static class NameRenderer implements Renderer<String> {
    private static Map<String, String> LC;

    @Override
    public String render(String varName) {
      if (Permission.isLabel(varName)) {
        return Util.M.label(new Permission(varName).getLabel());
      }

      Map<String, String> m = Util.C.permissionNames();
      String desc = m.get(varName);
      if (desc == null) {
        if (LC == null) {
          LC = new HashMap<String, String>();
          for (Map.Entry<String, String> e : m.entrySet()) {
            LC.put(e.getKey().toLowerCase(), e.getValue());
          }
        }
        desc = LC.get(varName.toLowerCase());
      }
      return desc != null ? desc : varName;
    }

    @Override
    public void render(String object, Appendable appendable) throws IOException {
      appendable.append(render(object));
    }
  }
}
