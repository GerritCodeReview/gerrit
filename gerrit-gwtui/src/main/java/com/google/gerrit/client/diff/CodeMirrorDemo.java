// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.diff;

import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.Configuration;

public class CodeMirrorDemo extends Screen {
  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private FlowPanel editorContainer;
  private CodeMirror cm;
  private HandlerRegistration resizeHandler;

  public CodeMirrorDemo(
      PatchSet.Id base,
      PatchSet.Id revision,
      String path) {
    this.base = base;
    this.revision = revision;
    this.path = path;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    add(editorContainer = new FlowPanel());
  }

  @Override
  protected void onLoad() {
    super.onLoad();

    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
      .ignoreWhitespace(DiffApi.IgnoreWhitespace.NONE)
      .get(new ScreenLoadCallback<DiffInfo>(this) {
        @Override
        protected void preDisplay(DiffInfo diff) {
          display(diff);
        }
      });
  }

  @Override
  public void onShowView() {
    super.onShowView();
    cm.refresh();
  }

  @Override
  protected void onUnload() {
    super.onUnload();

    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    if (cm != null) {
      cm.getWrapperElement().removeFromParent();
      cm = null;
    }
  }

  private void display(DiffInfo diff) {
    Configuration cfg = Configuration.create()
      .set("readOnly", true)
      .set("lineNumbers", true)
      .set("tabSize", 2)
      .set("value", diff.text_b());
    if (diff.meta_b() != null && diff.meta_b().content_type() != null) {
      String mode = diff.meta_b().content_type();
      if ("text/x-java-source".equals(mode)) {
        mode = "text/x-java";
      }
      cfg.set("mode", mode);
    }

    cm = CodeMirror.create(editorContainer.getElement(), cfg);
    cm.setWidth("100%");
    cm.setHeight(Window.getClientHeight() - HEADER_FOOTER);
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        cm.setHeight(event.getHeight() - HEADER_FOOTER);
        cm.refresh();
      }
    });
  }
}
