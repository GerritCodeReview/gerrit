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

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ProjectAccess;
import com.google.gwt.core.client.GWT;
import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.EditorDelegate;
import com.google.gwt.editor.client.ValueAwareEditor;
import com.google.gwt.editor.client.adapters.EditorSource;
import com.google.gwt.editor.client.adapters.ListEditor;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
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
  FlowPanel localContainer;
  ListEditor<AccessSection, AccessSectionEditor> local;

  @UiField
  Button newSection;

  private ProjectAccess value;

  public ProjectAccessEditor() {
    initWidget(uiBinder.createAndBindUi(this));
    local = ListEditor.of(new Source(localContainer));
  }

  @UiHandler("newSection")
  void onNewSection(ClickEvent event) {
    AccessSection section = new AccessSection("refs/heads/*");
    local.getList().add(section);
  }

  @Override
  public void setValue(ProjectAccess value) {
    this.value = value;
    newSection.setVisible(value != null && !value.getOwnerOf().isEmpty());
  }

  @Override
  public void flush() {
    List<AccessSection> src = local.getList();
    List<AccessSection> keep = new ArrayList<AccessSection>(src.size());

    for (int i = 0; i < src.size(); i++) {
      AccessSectionEditor e = (AccessSectionEditor) localContainer.getWidget(i);
      if (!e.isDeleted()) {
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

  private static class Source extends EditorSource<AccessSectionEditor> {
    private final FlowPanel container;

    Source(FlowPanel container) {
      this.container = container;
    }

    @Override
    public AccessSectionEditor create(int index) {
      AccessSectionEditor subEditor = new AccessSectionEditor();
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
