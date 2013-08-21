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

import static com.google.gerrit.client.diff.DisplaySide.A;
import static com.google.gerrit.client.diff.DisplaySide.B;
import static com.google.gerrit.client.diff.OverviewBar.MarkType.DELETE;
import static com.google.gerrit.client.diff.OverviewBar.MarkType.EDIT;
import static com.google.gerrit.client.diff.OverviewBar.MarkType.INSERT;

import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.EventListener;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.LineWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Colors modified regions for {@link SideBySide2}. */
class SideBySideChunkManager extends ChunkManager {
  private static final JavaScriptObject focusA = initOnClick(A);
  private static final JavaScriptObject focusB = initOnClick(B);
  private static final native JavaScriptObject initOnClick(DisplaySide s) /*-{
    return $entry(function(e){
      @com.google.gerrit.client.diff.SideBySideChunkManager::focus(
        Lcom/google/gwt/dom/client/NativeEvent;
        Lcom/google/gerrit/client/diff/DisplaySide;)(e,s)
    });
  }-*/;

  private static void focus(NativeEvent event, DisplaySide side) {
    Element e = Element.as(event.getEventTarget());
    for (e = DOM.getParent(e); e != null; e = DOM.getParent(e)) {
      EventListener l = DOM.getEventListener(e);
      if (l instanceof SideBySide2) {
        ((SideBySide2) l).getCmFromSide(side).focus();
        event.stopPropagation();
      }
    }
  };

  static void focusOnClick(Element e, DisplaySide side) {
    onClick(e, side == A ? focusA : focusB);
  }

  private static final native void onClick(Element e, JavaScriptObject f)
  /*-{ e.onclick = f }-*/;

  private final CodeMirror cmA;
  private final CodeMirror cmB;

  private List<LineWidget> padding;

  SideBySideChunkManager(SideBySide2 host,
      CodeMirror cmA,
      CodeMirror cmB,
      OverviewBar sidePanel) {
    super(host, sidePanel);

    this.cmA = cmA;
    this.cmB = cmB;
  }

  @Override
  void reset() {
    super.reset();

    for (LineWidget w : padding) {
      w.clear();
    }
  }

  void render(DiffInfo diff) {
    super.render(diff);

    padding = new ArrayList<>();

    String diffColor = diff.meta_a() == null || diff.meta_b() == null
        ? SideBySideTable2.style.intralineBg()
        : SideBySideTable2.style.diff();

    for (Region current : Natives.asList(diff.content())) {
      if (current.ab() != null) {
        getMapper().appendCommon(current.ab().length());
      } else if (current.skip() > 0) {
        getMapper().appendCommon(current.skip());
      } else if (current.common()) {
        getMapper().appendCommon(current.b().length());
      } else {
        render(current, diffColor);
      }
    }
  }

  private void render(Region region, String diffColor) {
    int startA = getMapper().getLineA();
    int startB = getMapper().getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;

    String color = a == null || b == null
        ? diffColor
        : SideBySideTable2.style.intralineBg();

    colorLines(cmA, color, startA, aLen);
    colorLines(cmB, color, startB, bLen);
    markEdit(cmA, startA, a, region.edit_a());
    markEdit(cmB, startB, b, region.edit_b());
    addPadding(cmA, startA + aLen - 1, bLen - aLen);
    addPadding(cmB, startB + bLen - 1, aLen - bLen);
    addGutterTag(region, startA, startB);
    getMapper().appendReplace(aLen, bLen);

    int endA = getMapper().getLineA() - 1;
    int endB = getMapper().getLineB() - 1;
    if (aLen > 0) {
      addDiffChunk(cmA, endA, aLen, bLen > 0);
    }
    if (bLen > 0) {
      addDiffChunk(cmB, endB, bLen, aLen > 0);
    }
  }

  private void addGutterTag(Region region, int startA, int startB) {
    if (region.a() == null) {
      getOverviewBar().add(cmB, startB, region.b().length(), INSERT);
    } else if (region.b() == null) {
      getOverviewBar().add(cmA, startA, region.a().length(), DELETE);
    } else {
      getOverviewBar().add(cmB, startB, region.b().length(), EDIT);
    }
  }

  private void markEdit(CodeMirror cm, int startLine,
      JsArrayString lines, JsArray<Span> edits) {
    if (lines == null || edits == null) {
      return;
    }

    EditIterator iter = new EditIterator(lines, startLine);
    Configuration bg = Configuration.create()
        .set("className", SideBySideTable2.style.intralineBg())
        .set("readOnly", true);

    Configuration diff = Configuration.create()
        .set("className", SideBySideTable2.style.diff())
        .set("readOnly", true);

    LineCharacter last = CodeMirror.pos(0, 0);
    for (Span span : Natives.asList(edits)) {
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      if (from.getLine() == last.getLine()) {
        addTextMarker(cm.markText(last, from, bg));
      } else {
        addTextMarker(cm.markText(CodeMirror.pos(from.getLine(), 0), from, bg));
      }
      addTextMarker(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          SideBySideTable2.style.diff(),
          from.getLine(), to.getLine());
    }
  }

  /**
   * Insert a new padding div below the given line.
   *
   * @param cm parent CodeMirror to add extra space into.
   * @param line line to put the padding below.
   * @param len number of lines to pad. Padding is inserted only if
   *        {@code len >= 1}.
   */
  private void addPadding(CodeMirror cm, int line, int len) {
    if (0 < len) {
      // DiffTable adds 1px bottom padding to each line to preserve
      // sufficient space for underscores commonly appearing in code.
      // Padding should be 1em + 1px high for each line. Add within
      // the browser using height + padding-bottom.
      Element pad = DOM.createDiv();
      pad.setClassName(SideBySideTable2.style.padding());
      pad.getStyle().setHeight(len, Unit.EM);
      pad.getStyle().setPaddingBottom(len, Unit.PX);
      focusOnClick(pad, cm.side());
      padding.add(cm.addLineWidget(
        line == -1 ? 0 : line,
        pad,
        Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true)
          .set("above", line == -1)));
    }
  }

  private void addDiffChunk(CodeMirror cm, int lineOnOther, int chunkSize, boolean edit) {
    addDiffChunk(new DiffChunkInfo(cm.side(), lineOnOther - chunkSize + 1, lineOnOther, edit));
  }

  Runnable diffChunkNav(final CodeMirror cm, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.hasActiveLine() ? cm.getLineNumber(cm.getActiveLine()) : 0;
        int res = Collections.binarySearch(
                getChunks(),
                new DiffChunkInfo(cm.side(), line, 0, false),
                getDiffChunkComparator());
        diffChunkNavHelper(cm, res, dir);
      }
    };
  }

  /*DiffChunkInfo getDiffChunk(DisplaySide side, int line) {
    int res = Collections.binarySearch(
        chunks,
        new DiffChunkInfo(side, line, 0, false), // Dummy DiffChunkInfo
        getDiffChunkComparator());
    if (res >= 0) {
      return chunks.get(res);
    } else { // The line might be within a DiffChunk
      res = -res - 1;
      if (res > 0) {
        DiffChunkInfo info = chunks.get(res - 1);
        if (info.getSide() == side && info.getStart() <= line &&
            line <= info.getEnd()) {
          return info;
        }
      }
    }
    return null;
  }*/
}
