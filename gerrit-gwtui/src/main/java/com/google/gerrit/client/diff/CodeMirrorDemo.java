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

import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.ModeInjector;

public class CodeMirrorDemo extends Screen {
  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private DiffTable diffTable;
  private CodeMirror cmA;
  private CodeMirror cmB;
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
    add(diffTable = new DiffTable());

  }

  @Override
  protected void onLoad() {
    super.onLoad();

    CallbackGroup group = new CallbackGroup();
    CodeMirror.initLibrary(group.add(new GerritCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
      }
    }));
    DiffApi.diff(revision, path)
      .base(base)
      .wholeFile()
      .ignoreWhitespace(DiffApi.IgnoreWhitespace.NONE)
      .get(group.add(new GerritCallback<DiffInfo>() {
        @Override
        public void onSuccess(final DiffInfo diff) {
          new ModeInjector()
            .add(getContentType(diff.meta_a()))
            .add(getContentType(diff.meta_b()))
            .inject(new ScreenLoadCallback<Void>(CodeMirrorDemo.this){
              @Override
              protected void preDisplay(Void result) {
                display(diff);
              }
            });
        }
      }));
  }

  @Override
  public void onShowView() {
    super.onShowView();
    if (cmA != null) {
      cmA.refresh();
    }
    if (cmB != null) {
      cmB.refresh();
    }
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (resizeHandler != null) {
      resizeHandler.removeHandler();
      resizeHandler = null;
    }
    if (cmA != null) {
      cmA.getWrapperElement().removeFromParent();
      cmA = null;
    }
    if (cmB != null) {
      cmB.getWrapperElement().removeFromParent();
      cmB = null;
    }
  }

  private void display(DiffInfo diff) {
    cmA = displaySide(diff.meta_a(), diff.text_a(), diffTable.getCmA());
    cmB = displaySide(diff.meta_b(), diff.text_b(), diffTable.getCmB());
    render(diff);
    resizeHandler = Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        if (cmA != null) {
          cmA.setHeight(event.getHeight() - HEADER_FOOTER);
          cmA.refresh();
        }
        if (cmB != null) {
          cmB.setHeight(event.getHeight() - HEADER_FOOTER);
          cmB.refresh();
        }
      }
    });
  }

  private CodeMirror displaySide(DiffInfo.FileMeta meta, String contents,
      Element ele) {
    if (meta == null) {
      return null; // TODO: Handle empty contents
    }
    Configuration cfg = Configuration.create()
      .set("readOnly", true)
      .set("lineNumbers", true)
      .set("tabSize", 2)
      .set("mode", getContentType(meta))
      .set("value", contents)
      .setInfinity("viewportMargin");
    final CodeMirror cm = CodeMirror.create(ele, cfg);
    cm.setWidth("100%");
    cm.setHeight(Window.getClientHeight() - HEADER_FOOTER);
    return cm;
  }

  private void render(DiffInfo diff) {
    JsArray<Region> regions = diff.content();
    int lineA = 0, lineB = 0;
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      if (current.ab() != null) {
        lineA += current.ab().length();
        lineB += current.ab().length();
      } else if (current.a() == null && current.b() != null) {
        int delta = current.b().length();
        padLines(cmA, delta, lineA - 1);
        lineB = colorLine(delta, lineA, lineB, true);
      } else if (current.a() != null && current.b() == null) {
        int delta = current.a().length();
        padLines(cmB, delta, lineB - 1);
        lineA = colorLine(delta, lineA, lineB, false);
      } else {
        lineA = colorLine(current.a().length(), lineA, lineB, false);
        lineB = colorLine(current.b().length(), lineA, lineB, true);
      }
    }
  }

  private void padLines(CodeMirror cm, int delta, int where) {
    for (int i = 0; i < delta; i++) {
      genPadding(cm, where);
    }
  }

  private void genPadding(CodeMirror cm, int line) {
    Element div = DOM.createDiv();
    div.setClassName(diffTable.style.padding());
    cm.addLineWidget(line, div,
        Configuration.create().set("coverGutter", true));
  }

  private int colorLine(int delta, int lineA, int lineB, boolean isInsert) {
    CodeMirror cm = isInsert ? cmB : cmA;
    String style = isInsert ?
        diffTable.style.insert() : diffTable.style.delete();
    int line = isInsert ? lineB : lineA;
    for (int i = 0; i < delta; i++) {
      cm.addLineClass(line, LineClassWhere.WRAP, style);
      line++;
    }
    return line;
  }

  private static String getContentType(DiffInfo.FileMeta meta) {
    return meta != null && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type())
        : null;
  }
}
