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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupInfo;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.editor.client.adapters.EditorSource;
import com.google.gwt.editor.client.adapters.ListEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ValueLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PermissionEditor extends Composite
    implements Editor<Permission>, ValueAwareEditor<Permission> {
  interface Binder extends UiBinder<HTMLPanel, PermissionEditor> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField(provided = true)
  @Path("name")
  ValueLabel<String> normalName;

  @UiField(provided = true)
  @Path("name")
  ValueLabel<String> deletedName;

  @UiField CheckBox exclusiveGroup;

  @UiField FlowPanel ruleContainer;
  ListEditor<PermissionRule, PermissionRuleEditor> rules;

  @UiField DivElement addContainer;
  @UiField DivElement addStage1;
  @UiField DivElement addStage2;
  @UiField Anchor beginAddRule;
  @UiField @Editor.Ignore GroupReferenceBox groupToAdd;
  @UiField Button addRule;

  @UiField Anchor deletePermission;

  @UiField DivElement normal;
  @UiField DivElement deleted;

  private final Project.NameKey projectName;
  private final Map<AccountGroup.UUID, GroupInfo> groupInfo;
  private final boolean readOnly;
  private final AccessSection section;
  private final LabelTypes labelTypes;
  private Permission value;
  private PermissionRange.WithDefaults validRange;
  private boolean isDeleted;

  public PermissionEditor(
      ProjectAccess projectAccess, boolean readOnly, AccessSection section, LabelTypes labelTypes) {
    this.readOnly = readOnly;
    this.section = section;
    this.projectName = projectAccess.getProjectName();
    this.groupInfo = projectAccess.getGroupInfo();
    this.labelTypes = labelTypes;

    PermissionNameRenderer nameRenderer =
        new PermissionNameRenderer(projectAccess.getCapabilities());
    normalName = new ValueLabel<>(nameRenderer);
    deletedName = new ValueLabel<>(nameRenderer);

    initWidget(uiBinder.createAndBindUi(this));
    groupToAdd.setProject(projectName);
    rules = ListEditor.of(new RuleEditorSource());

    exclusiveGroup.setEnabled(!readOnly);
    exclusiveGroup.setVisible(RefConfigSection.isValid(section.getName()));

    if (readOnly) {
      addContainer.removeFromParent();
      addContainer = null;

      deletePermission.removeFromParent();
      deletePermission = null;
    }
  }

  @UiHandler("deletePermission")
  void onDeleteHover(@SuppressWarnings("unused") MouseOverEvent event) {
    addStyleName(AdminResources.I.css().deleteSectionHover());
  }

  @UiHandler("deletePermission")
  void onDeleteNonHover(@SuppressWarnings("unused") MouseOutEvent event) {
    removeStyleName(AdminResources.I.css().deleteSectionHover());
  }

  @UiHandler("deletePermission")
  void onDeletePermission(@SuppressWarnings("unused") ClickEvent event) {
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

  @UiHandler("beginAddRule")
  void onBeginAddRule(@SuppressWarnings("unused") ClickEvent event) {
    beginAddRule();
  }

  void beginAddRule() {
    addStage1.getStyle().setDisplay(Display.NONE);
    addStage2.getStyle().setDisplay(Display.BLOCK);

    Scheduler.get()
        .scheduleDeferred(
            new ScheduledCommand() {
              @Override
              public void execute() {
                groupToAdd.setFocus(true);
              }
            });
  }

  @UiHandler("addRule")
  void onAddGroupByClick(@SuppressWarnings("unused") ClickEvent event) {
    GroupReference ref = groupToAdd.getValue();
    if (ref != null) {
      addGroup(ref);
    } else {
      groupToAdd.setFocus(true);
    }
  }

  @UiHandler("groupToAdd")
  void onAddGroupByEnter(SelectionEvent<GroupReference> event) {
    GroupReference ref = event.getSelectedItem();
    if (ref != null) {
      addGroup(ref);
    }
  }

  @UiHandler("groupToAdd")
  void onAbortAddGroup(@SuppressWarnings("unused") CloseEvent<GroupReferenceBox> event) {
    hideAddGroup();
  }

  @UiHandler("hideAddGroup")
  void hideAddGroup(@SuppressWarnings("unused") ClickEvent event) {
    hideAddGroup();
  }

  private void hideAddGroup() {
    addStage1.getStyle().setDisplay(Display.BLOCK);
    addStage2.getStyle().setDisplay(Display.NONE);
  }

  private void addGroup(GroupReference ref) {
    if (ref.getUUID() != null) {
      if (value.getRule(ref) == null) {
        PermissionRule newRule = value.getRule(ref, true);
        if (validRange != null) {
          int min = validRange.getDefaultMin();
          int max = validRange.getDefaultMax();
          newRule.setRange(min, max);

        } else if (GlobalCapability.PRIORITY.equals(value.getName())) {
          newRule.setAction(PermissionRule.Action.BATCH);
        }

        rules.getList().add(newRule);
      }
      groupToAdd.setValue(null);
      groupToAdd.setFocus(true);

    } else {
      // If the oracle didn't get to complete a UUID, resolve it now.
      //
      addRule.setEnabled(false);
      GroupMap.suggestAccountGroupForProject(
          projectName.get(),
          ref.getName(),
          1,
          new GerritCallback<GroupMap>() {
            @Override
            public void onSuccess(GroupMap result) {
              addRule.setEnabled(true);
              if (result.values().length() == 1) {
                addGroup(
                    new GroupReference(
                        result.values().get(0).getGroupUUID(), result.values().get(0).name()));
              } else {
                groupToAdd.setFocus(true);
                new ErrorDialog(Gerrit.M.noSuchGroupMessage(ref.getName())).center();
              }
            }

            @Override
            public void onFailure(Throwable caught) {
              addRule.setEnabled(true);
              super.onFailure(caught);
            }
          });
    }
  }

  boolean isDeleted() {
    return isDeleted;
  }

  @Override
  public void setValue(Permission value) {
    this.value = value;

    if (Permission.hasRange(value.getName())) {
      LabelType lt = labelTypes.byLabel(value.getLabel());
      if (lt != null) {
        validRange =
            new PermissionRange.WithDefaults(
                value.getName(),
                lt.getMin().getValue(),
                lt.getMax().getValue(),
                lt.getMin().getValue(),
                lt.getMax().getValue());
      }
    } else if (GlobalCapability.isCapability(value.getName())) {
      validRange = GlobalCapability.getRange(value.getName());

    } else {
      validRange = null;
    }

    if (Permission.OWNER.equals(value.getName())) {
      exclusiveGroup.setEnabled(false);
    } else {
      exclusiveGroup.setEnabled(!readOnly);
    }
  }

  @Override
  public void flush() {
    List<PermissionRule> src = rules.getList();
    List<PermissionRule> keep = new ArrayList<>(src.size());

    for (int i = 0; i < src.size(); i++) {
      PermissionRuleEditor e = (PermissionRuleEditor) ruleContainer.getWidget(i);
      if (!e.isDeleted()) {
        keep.add(src.get(i));
      }
    }
    value.setRules(keep);
  }

  @Override
  public void onPropertyChange(String... paths) {}

  @Override
  public void setDelegate(EditorDelegate<Permission> delegate) {}

  private class RuleEditorSource extends EditorSource<PermissionRuleEditor> {
    @Override
    public PermissionRuleEditor create(int index) {
      PermissionRuleEditor subEditor =
          new PermissionRuleEditor(readOnly, groupInfo, section, value, validRange);
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
}
