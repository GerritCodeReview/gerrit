// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client.editor;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.VoidResult;
import com.google.gerrit.client.changes.ChangeFileApi;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HTMLPanel;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;

public class EditScreen extends Screen {

  interface Binder extends UiBinder<HTMLPanel, EditScreen> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  private final PatchSet.Id revision;
  private final String path;
  private CodeMirror cm3;

  @UiField Element filePath;
  @UiField Button cancel;
  @UiField Button save;
  @UiField Element editor;

  public EditScreen(Patch.Key patch) {
    this.revision = patch.getParentKey();
    this.path = patch.get();
    add(uiBinder.createAndBindUi(this));
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setHeaderVisible(false);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    CallbackGroup cmGroup = new CallbackGroup();
    CodeMirror.initLibrary(cmGroup.add(CallbackGroup.<Void> emptyCallback()));
    initPath();
    ChangeFileApi.getContent(revision, path,
        new ScreenLoadCallback<String>(this) {
          @Override
          protected void preDisplay(String content) {
            createEditor(content);
          }
        });
  }

  @Override
  public void onShowView() {
    super.onShowView();
    int rest = Gerrit.getHeaderFooterHeight()
        + 30; // Estimate
    cm3.setHeight(Window.getClientHeight() - rest);
    cm3.refresh();
  }

  private void createEditor(String content) {
    cm3 = CodeMirror.create(editor, getConfig());
    cm3.setValue(content);
  }

  private Configuration getConfig() {
    // TODO(davido): Retrieve config from own per user editor configuration
    return Configuration.create()
        .set("readOnly", false)
        .set("cursorBlinkRate", 0)
        .set("cursorHeight", 0.85)
        .set("lineNumbers", true)
        .set("tabSize", 4)
        // TODO(davido): Retrieve file mode and set it in CM3
        //.set("mode", mode)
        .set("lineWrapping", false)
        .set("styleSelectedText", true)
        .set("showTrailingSpace", true)
        .set("keyMap", "default");
  }

  private void initPath() {
    filePath.setInnerText(path);
  }

  @UiHandler("save")
  void onSave(@SuppressWarnings("unused") ClickEvent e) {
    ChangeFileApi.putContent(revision, path, cm3.getValue(),
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            Gerrit.display(PageLinks.toChangeInEditMode(
                revision.getParentKey()));
          }
        });
  }

  @UiHandler("cancel")
  void onCancel(@SuppressWarnings("unused") ClickEvent e) {
    Gerrit.display(PageLinks.toChangeInEditMode(revision.getParentKey()));
  }
}