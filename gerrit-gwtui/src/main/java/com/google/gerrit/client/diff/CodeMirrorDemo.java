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
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.ModeInjector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CodeMirrorDemo extends Screen {
  private static final int HEADER_FOOTER = 60 + 15 * 2 + 38;
  private final PatchSet.Id base;
  private final PatchSet.Id revision;
  private final String path;

  private DiffTable diffTable;
  private CodeMirror cmA;
  private CodeMirror cmB;
  private HandlerRegistration resizeHandler;
  private List<LineNumberDiff> lineMapAtoB;
  private List<LineNumberDiff> lineMapBtoA;
  private NativeMap<JsArray<CommentInfo>> commentMap;
  private NativeMap<JsArray<CommentInfo>> draftMap;

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
      public void onSuccess(
          final NativeMap<JsArray<CommentInfo>> cMap) {
        commentMap = cMap;
      }
    }));
    CommentApi.drafts(revision,
        group.add(new GerritCallback<NativeMap<JsArray<CommentInfo>>>() {
      @Override
      public void onSuccess(
          final NativeMap<JsArray<CommentInfo>> dMap) {
        draftMap = dMap;
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
    renderComments(commentMap.get(path), false);
    renderComments(draftMap.get(path), true);
    commentMap = null;
    draftMap = null;
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
    Window.enableScrolling(false);
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
    int lineA = 0, lineB = 0, numRegions = regions.length();
    lineMapAtoB = new ArrayList<LineNumberDiff>(numRegions);
    lineMapBtoA = new ArrayList<LineNumberDiff>(numRegions);
    for (int i = 0; i < numRegions; i++) {
      Region current = regions.get(i);
      if (current.ab() != null) { // Common
        lineA += current.ab().length();
        lineB += current.ab().length();
      } else if (current.a() == null && current.b() != null) { // Insertion
        int delta = current.b().length();
        insertEmptyLines(cmA, lineA, delta);
        lineB = colorLines(cmB, lineB, delta);
        int bAheadOfA = lineB - lineA;
        lineMapAtoB.add(new LineNumberDiff(lineA, lineA, bAheadOfA));
        lineMapBtoA.add(new LineNumberDiff(lineA, lineB, -bAheadOfA));
      } else if (current.a() != null && current.b() == null) { // Deletion
        int delta = current.a().length();
        insertEmptyLines(cmB, lineB, delta);
        lineA = colorLines(cmA, lineA, delta);
        int aAheadOfB = lineA - lineB;
        lineMapAtoB.add(new LineNumberDiff(lineA, lineB, -aAheadOfB));
        lineMapBtoA.add(new LineNumberDiff(lineA, lineA, aAheadOfB));
      } else { // Edit
        JsArrayString currentA = current.a();
        JsArrayString currentB = current.b();
        int aLength = currentA.length();
        int bLength = currentB.length();
        int origLineA = lineA;
        int origLineB = lineB;
        lineA = colorLines(cmA, lineA, aLength);
        lineB = colorLines(cmB, lineB, bLength);
        if (aLength < bLength) { // Edit with insertion
          int delta = bLength - aLength;
          insertEmptyLines(cmA, lineA, delta);
          int bAheadOfA = lineB - lineA;
          lineMapAtoB.add(new LineNumberDiff(lineA, lineA, bAheadOfA));
          lineMapBtoA.add(new LineNumberDiff(lineB - delta, lineB, -bAheadOfA));
        } else if (aLength > bLength) { // Edit with deletion
          int delta = aLength - bLength;
          insertEmptyLines(cmB, lineB, delta);
          int aAheadOfB = lineA - lineB;
          lineMapAtoB.add(new LineNumberDiff(lineA - delta, lineA, -aAheadOfB));
          lineMapBtoA.add(new LineNumberDiff(lineA, lineA, aAheadOfB));
        }
        markEdit(cmA, currentA, current.edit_a(), origLineA);
        markEdit(cmB, currentB, current.edit_b(), origLineB);
      }
    }
  }

  private void insertEmptyLines(CodeMirror cm, int line, int cnt) {
    addPaddingWidget(cm, diffTable.style.padding(), line, cnt, Unit.EM);
  }

  private Element addPaddingWidget(CodeMirror cm, String style, int line,
      int height, Unit unit) {
    Element div = DOM.createDiv();
    div.setClassName(style);
    div.getStyle().setHeight(height, unit);
    Configuration config = Configuration.create()
        .set("coverGutter", true)
        .set("above", line == 0);
    // CM is zero-indexed, not one-indexed
    cm.addLineWidget(line == 0 ? 0 : line - 1, div, config);
    return div;
  }

  private int colorLines(CodeMirror cm, int line, int cnt) {
    for (int i = 0; i < cnt; i++) {
      cm.addLineClass(line + i, LineClassWhere.WRAP, diffTable.style.diff());
    }
    return line + cnt;
  }

  private void markEdit(CodeMirror cm, JsArrayString lines,
      JsArray<Span> edits, int startLine) {
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

  private void renderComments(JsArray<CommentInfo> comments, boolean isDraft) {
    for (int i = 0; i < comments.length(); i++) {
      CommentInfo info = comments.get(i);
      CodeMirror cm = info.side() == Side.PARENT ? cmA : cmB;
      CodeMirror other = cm.equals(cmA) ? cmB : cmA;
      final CommentBox box = new CommentBox(info.author(), info.updated(),
          info.message(), isDraft);
      RootPanel.get().add(box);
      int line = info.line();
      Configuration config = Configuration.create().set("coverGutter", true);
      cm.addLineWidget(line == 0 ? 0 : (line - 1), box.getElement(), config);
      int lineToPad = lineOnOther(other, line);
      // TODO: +2 to compensate for border width, need more systematic approach
      final Element paddingOtherside = addPaddingWidget(other,
          diffTable.style.padding(), lineToPad,
          box.getOffsetHeight() + 2, Unit.PX);
      box.addClickHandler(new Runnable() {
        @Override
        public void run() {
          paddingOtherside.getStyle().setHeight(box.getOffsetHeight(),
              Unit.PX);
        }
      });
    }
  }

  private int lineOnOther(CodeMirror other, int line) {
    List<LineNumberDiff> map = other == cmA ? lineMapBtoA : lineMapAtoB;
    int ret = Collections.binarySearch(map, new LineNumberDiff(line));
    int index = -ret - 1;
    if (index == 0) {
      return line;
    } else {
      LineNumberDiff lookup = map.get(index);
      int high = lookup.high;
      int delta = lookup.delta;
      if (lookup.low <= line && line <= high) {
        return high + delta;
      } else {
        return line + delta;
      }
    }
  }

  private Runnable doScroll(final CodeMirror cm) {
    final CodeMirror other = cm == cmA ? cmB : cmA;
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

  private static class LineNumberDiff implements Comparable<LineNumberDiff> {
    private final int low;
    private final int high;
    private final int delta;

    private LineNumberDiff(int low, int high, int delta) {
      this.low = low;
      this.high = high;
      this.delta = delta;
    }

    private LineNumberDiff(int line) {
      this(line, 0, 0);
    }

    @Override
    public int compareTo(LineNumberDiff o) {
      return low - o.low;
    }
  }
}
