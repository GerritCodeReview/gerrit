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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.GitwebLink;
import com.google.gerrit.client.ui.Hyperlink;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.editor.client.adapters.EditorSource;
import com.google.gwt.editor.client.adapters.ListEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTMLPanel;

import java.util.ArrayList;
import java.util.List;

public class ProjectAccessEditor extends Composite implements
    Editor<ProjectAccess>, ValueAwareEditor<ProjectAccess> {
  interface Binder extends UiBinder<HTMLPanel, ProjectAccessEditor> {
  }

  private static final Binder uiBinder = GWT.create(Binder.class);

  @UiField
  DivElement inheritsFrom;

  @UiField
  Hyperlink parentProject;

  @UiField
  DivElement history;

  @UiField
  Anchor gitweb;

  @UiField
  FlowPanel localContainer;
  ListEditor<AccessSection, AccessSectionEditor> local;

  @UiField
  Anchor addSection;

  private ProjectAccess value;

  private boolean editing;

  public ProjectAccessEditor() {
    initWidget(uiBinder.createAndBindUi(this));
    local = ListEditor.of(new Source(localContainer));
  }

  @UiHandler("addSection")
  void onAddSection(ClickEvent event) {
    int index = local.getList().size();
    local.getList().add(new AccessSection("refs/heads/*"));

    AccessSectionEditor editor = local.getEditors().get(index);
    editor.enableEditing();
    editor.editRefPattern();
  }

  @Override
  public void setValue(ProjectAccess value) {
    // If the owner can edit the Global Capabilities but they don't exist in this
    // project, create an empty one at the beginning of the list making it
    // possible to add permissions to it.
    if (editing
        && value.isOwnerOf(AccessSection.GLOBAL_CAPABILITIES)
        && value.getLocal(AccessSection.GLOBAL_CAPABILITIES) == null) {
      value.getLocal().add(0, new AccessSection(AccessSection.GLOBAL_CAPABILITIES));
    }

    // Same for Project Context Capabilities
    if (editing
        && value.isOwnerOf(AccessSection.PROJECT_CONTEXT_CAPABILITIES)
        && value.getLocal(AccessSection.PROJECT_CONTEXT_CAPABILITIES) == null
        ) {
      value.getLocal().add(0, new AccessSection(
          AccessSection.PROJECT_CONTEXT_CAPABILITIES));
    }

    this.value = value;

    Project.NameKey parent = value.getInheritsFrom();
    if (parent != null) {
      inheritsFrom.getStyle().setDisplay(Display.BLOCK);
      parentProject.setText(parent.get());
      parentProject.setTargetHistoryToken( //
          Dispatcher.toProjectAdmin(parent, ProjectScreen.ACCESS));
    } else {
      inheritsFrom.getStyle().setDisplay(Display.NONE);
    }

    final GitwebLink c = Gerrit.getGitwebLink();
    if (value.isConfigVisible() && c != null) {
      history.getStyle().setDisplay(Display.BLOCK);
      gitweb.setText(c.getLinkName());
      gitweb.setHref(c.toFileHistory(new Branch.NameKey(value.getProjectName(),
          "refs/meta/config"), "project.config"));
    } else {
      history.getStyle().setDisplay(Display.NONE);
    }

    addSection.setVisible(value != null && editing && (!value.getOwnerOf().isEmpty() || value.canUpload()));
  }

  @Override
  public void flush() {
    List<AccessSection> src = local.getList();
    List<AccessSection> keep = new ArrayList<AccessSection>(src.size());

    for (int i = 0; i < src.size(); i++) {
      AccessSectionEditor e = (AccessSectionEditor) localContainer.getWidget(i);
      if (!e.isDeleted() && !src.get(i).getPermissions().isEmpty()) {
        keep.add(src.get(i));
      }
    }
    value.setLocal(keep);
  }

  @Override
  public void onPropertyChange(String... paths) {
  }

  @Override
  public void setDelegate(EditorDelegate<ProjectAccess> delegate) {
  }

  void setEditing(final boolean editing) {
    this.editing = editing;
    addSection.setVisible(editing);
  }

  private class Source extends EditorSource<AccessSectionEditor> {
    private final FlowPanel container;

    Source(FlowPanel container) {
      this.container = container;
    }

    @Override
    public AccessSectionEditor create(int index) {
      AccessSectionEditor subEditor = new AccessSectionEditor(value);
      subEditor.setEditing(editing);
      container.insert(subEditor, index);
      return subEditor;
    }

    @Override
    public void dispose(AccessSectionEditor subEditor) {
      subEditor.removeFromParent();
    }

    @Override
    public void setIndex(AccessSectionEditor subEditor, int index) {
      container.insert(subEditor, index);
    }
  }
}
