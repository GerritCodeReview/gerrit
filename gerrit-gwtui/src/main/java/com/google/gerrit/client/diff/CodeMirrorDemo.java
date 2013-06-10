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

import com.google.gerrit.client.changes.CommentApi;
import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.ModeInjector;

import java.util.ArrayList;
import java.util.List;

public class CodeMirrorDemo extends Screen {
  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private static final JsArrayString EMPTY =
      (JsArrayString) JavaScriptObject.createArray();
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private DiffTable diffTable;
  private CodeMirror cmA;
  private CodeMirror cmB;
  private HandlerRegistration resizeHandler;
  private JsArray<CommentInfo> published;
  private JsArray<CommentInfo> drafts;
  private List<Runnable> resizeCallbacks;
  private LineMapper mapper;

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
    setHeaderVisible(false);
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
      .intraline()
      .ignoreWhitespace(DiffApi.IgnoreWhitespace.NONE)
      .get(group.add(new GerritCallback<DiffInfo>() {
        @Override
        public void onSuccess(final DiffInfo diff) {
          new ModeInjector()
            .add(getContentType(diff.meta_a()))
            .add(getContentType(diff.meta_b()))
            .inject(new ScreenLoadCallback<Void>(CodeMirrorDemo.this) {
              @Override
              protected void preDisplay(Void result) {
                display(diff);
              }
            });
        }
      }));
    CommentApi.comments(revision,
        group.add(new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> m) { published = m.get(path); }
    }));
    CommentApi.drafts(revision,
        group.add(new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(NativeMap<JsArray<CommentInfo>> m) { drafts = m.get(path); }
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
    Window.enableScrolling(false);
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        for (Runnable r: resizeCallbacks) {
          r.run();
        }
        resizeCallbacks = null;
      }
    });
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
    Window.enableScrolling(true);
  }

  private void display(DiffInfo diff) {
    cmA = displaySide(diff.meta_a(), diff.text_a(), diffTable.getCmA());
    cmB = displaySide(diff.meta_b(), diff.text_b(), diffTable.getCmB());
    render(diff);
    resizeCallbacks = new ArrayList<Runnable>();
    renderComments(published, false);
    renderComments(drafts, true);
    published = null;
    drafts = null;
    mapper = null;

    // TODO: Probably need horizontal resize
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
    cmA.on("scroll", doScroll(cmB));
    cmB.on("scroll", doScroll(cmA));
  }

  private CodeMirror displaySide(DiffInfo.FileMeta meta, String contents,
      Element ele) {
    if (meta == null) {
      contents = "";
    }
    Configuration cfg = Configuration.create()
      .set("readOnly", true)
      .set("lineNumbers", true)
      .set("tabSize", 2)
      .set("mode", getContentType(meta))
      .set("styleSelectedText", true)
      .set("value", contents);
    final CodeMirror cm = CodeMirror.create(ele, cfg);
    cm.setHeight(Window.getClientHeight() - HEADER_FOOTER);
    return cm;
  }

  private void render(DiffInfo diff) {
    JsArray<Region> regions = diff.content();
    mapper = new LineMapper();
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      int origLineA = mapper.getLineA();
      int origLineB = mapper.getLineB();
      if (current.ab() != null) { // Common
        // TODO: Handle skips.
        mapper.appendCommon(current.ab().length());
      } else { // Insert, Delete or Edit
        JsArrayString currentA = current.a() == null ? EMPTY : current.a();
        JsArrayString currentB = current.b() == null ? EMPTY : current.b();
        int aLength = currentA.length();
        int bLength = currentB.length();
        colorLines(cmA, origLineA, aLength);
        colorLines(cmB, origLineB, bLength);
        mapper.appendCommon(Math.min(aLength, bLength));
        if (aLength < bLength) { // Edit with insertion
          int insertCnt = bLength - aLength;
          insertEmptyLines(cmA, mapper.getLineA(), insertCnt);
          mapper.appendInsert(insertCnt);
        } else if (aLength > bLength) { // Edit with deletion
          int deleteCnt = aLength - bLength;
          insertEmptyLines(cmB, mapper.getLineB(), deleteCnt);
          mapper.appendDelete(deleteCnt);
        }
        markEdit(cmA, currentA, current.edit_a(), origLineA);
        markEdit(cmB, currentB, current.edit_b(), origLineB);
      }
    }
  }

  private void renderComments(JsArray<CommentInfo> comments, boolean isDraft) {
    Configuration config = Configuration.create().set("coverGutter", true);
    for (int i = 0; comments != null && i < comments.length(); i++) {
      CommentInfo info = comments.get(i);
      Side mySide = info.side();
      CodeMirror cm = mySide == Side.PARENT ? cmA : cmB;
      CodeMirror other = otherCM(cm);
      final CommentBox box = new CommentBox(info.author(), info.updated(),
          info.message(), isDraft);
      int line = info.line() - 1; // CommentInfo is 1-based, but CM is 0-based
      diffTable.add(box);
      cm.addLineWidget(line, box.getElement(), config);
      int lineToPad = mapper.lineOnOther(mySide, line);
      // Estimated height at 21px, fixed by deferring after display
      final Element paddingOtherside = addPaddingWidget(other,
          diffTable.style.padding(), lineToPad,
          21, Unit.PX);
      Runnable callback = new Runnable() {
        @Override
        public void run() {
          paddingOtherside.getStyle().setHeight(
              box.getOffsetHeight(), Unit.PX);
        }
      };
      resizeCallbacks.add(callback);
      box.setOpenCloseHandler(callback);
    }
  }

  private CodeMirror otherCM(CodeMirror me) {
    return me == cmA ? cmB : cmA;
  }

  private void markEdit(CodeMirror cm, JsArrayString lines,
      JsArray<Span> edits, int startLine) {
    if (edits == null) {
      return;
    }
    EditIterator iter = new EditIterator(lines, startLine);
    Configuration diffOpt = Configuration.create()
        .set("className", diffTable.style.diff())
        .set("readOnly", true);
    Configuration editOpt = Configuration.create()
        .set("className", diffTable.style.intraline())
        .set("readOnly", true);
    LineCharacter last = LineCharacter.create(0, 0);
    for (int i = 0; i < edits.length(); i++) {
      Span span = edits.get(i);
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      int fromLine = from.getLine();
      if (last.getLine() == fromLine) {
        cm.markText(last, from, diffOpt);
      } else {
        cm.markText(LineCharacter.create(fromLine, 0), from, diffOpt);
      }
      cm.markText(from, to, editOpt);
      last = to;
      for (int line = fromLine; line < to.getLine(); line++) {
        cm.addLineClass(line, LineClassWhere.BACKGROUND,
            diffTable.style.intraline());
      }
    }
  }

  private void colorLines(CodeMirror cm, int line, int cnt) {
    for (int i = 0; i < cnt; i++) {
      cm.addLineClass(line + i, LineClassWhere.WRAP, diffTable.style.diff());
    }
  }

  private void insertEmptyLines(CodeMirror cm, int nextLine, int cnt) {
    // -1 to compensate for the line we went past when this method is called.
    addPaddingWidget(cm, diffTable.style.padding(), nextLine - 1,
        cnt, Unit.EM);
  }

  private Element addPaddingWidget(CodeMirror cm, String style, int line,
      int height, Unit unit) {
    Element div = DOM.createDiv();
    div.setClassName(style);
    div.getStyle().setHeight(height, unit);
    Configuration config = Configuration.create()
        .set("coverGutter", true)
        .set("above", line == -1);
    cm.addLineWidget(line == -1 ? 0 : line, div, config);
    return div;
  }

  private Runnable doScroll(final CodeMirror cm) {
    final CodeMirror other = otherCM(cm);
    return new Runnable() {
      public void run() {
        cm.scrollToY(other.getScrollInfo().getTop());
      }
    };
  }

  private static String getContentType(DiffInfo.FileMeta meta) {
    return meta != null && meta.content_type() != null
        ? ModeInjector.getContentType(meta.content_type())
        : null;
  }

  static class EditIterator {
    private final JsArrayString lines;
    private final int startLine;
    private int currLineIndex;
    private int currLineOffset;

    EditIterator(JsArrayString lineArray, int start) {
      lines = lineArray;
      startLine = start;
    }

    LineCharacter advance(int numOfChar) {
      while (currLineIndex < lines.length()) {
        int lengthWithNewline =
            lines.get(currLineIndex).length() - currLineOffset + 1;
        if (numOfChar < lengthWithNewline) {
          LineCharacter at = LineCharacter.create(
              startLine + currLineIndex,
              numOfChar + currLineOffset);
          currLineOffset += numOfChar;
          return at;
        }
        numOfChar -= lengthWithNewline;
        advanceLine();
      }
      throw new IllegalStateException("EditIterator index out of bound");
    }

    private void advanceLine() {
      currLineIndex++;
      currLineOffset = 0;
    }
  }
}
