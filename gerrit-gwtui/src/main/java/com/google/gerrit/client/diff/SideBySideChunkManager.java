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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.Pos;

/** Colors modified regions for {@link SideBySide}. */
class SideBySideChunkManager extends ChunkManager {
  private static final String DATA_LINES = "_cs2h";
  private static double guessedLineHeightPx = 15;
  private static final JavaScriptObject focusA = initOnClick(A);
  private static final JavaScriptObject focusB = initOnClick(B);

  private static native JavaScriptObject initOnClick(DisplaySide s) /*-{
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
      if (l instanceof SideBySide) {
        ((SideBySide) l).getCmFromSide(side).focus();
        event.stopPropagation();
      }
    }
  }

  static void focusOnClick(Element e, DisplaySide side) {
    onClick(e, side == A ? focusA : focusB);
  }

  private final SideBySide host;
  private final CodeMirror cmA;
  private final CodeMirror cmB;

  private List<DiffChunkInfo> chunks;
  private List<LineWidget> padding;
  private List<Element> paddingDivs;

  SideBySideChunkManager(SideBySide host, CodeMirror cmA, CodeMirror cmB, Scrollbar scrollbar) {
    super(scrollbar);

    this.host = host;
    this.cmA = cmA;
    this.cmB = cmB;
  }

  @Override
  DiffChunkInfo getFirst() {
    return !chunks.isEmpty() ? chunks.get(0) : null;
  }

  @Override
  void reset() {
    super.reset();

    for (LineWidget w : padding) {
      w.clear();
    }
  }

  @Override
  void render(DiffInfo diff) {
    super.render();

    chunks = new ArrayList<>();
    padding = new ArrayList<>();
    paddingDivs = new ArrayList<>();

    String diffColor =
        diff.metaA() == null || diff.metaB() == null
            ? SideBySideTable.style.intralineBg()
            : SideBySideTable.style.diff();

    for (Region current : Natives.asList(diff.content())) {
      if (current.ab() != null) {
        lineMapper.appendCommon(current.ab().length());
      } else if (current.skip() > 0) {
        lineMapper.appendCommon(current.skip());
      } else if (current.common()) {
        lineMapper.appendCommon(current.b().length());
      } else {
        render(current, diffColor);
      }
    }

    if (paddingDivs.isEmpty()) {
      paddingDivs = null;
    }
  }

  void adjustPadding() {
    if (paddingDivs != null) {
      double h = cmB.extras().lineHeightPx();
      for (Element div : paddingDivs) {
        int lines = div.getPropertyInt(DATA_LINES);
        div.getStyle().setHeight(lines * h, Unit.PX);
      }
      for (LineWidget w : padding) {
        w.changed();
      }
      paddingDivs = null;
      guessedLineHeightPx = h;
    }
  }

  private void render(Region region, String diffColor) {
    int startA = lineMapper.getLineA();
    int startB = lineMapper.getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;

    String color = a == null || b == null ? diffColor : SideBySideTable.style.intralineBg();

    colorLines(cmA, color, startA, aLen);
    colorLines(cmB, color, startB, bLen);
    markEdit(cmA, startA, a, region.editA());
    markEdit(cmB, startB, b, region.editB());
    addPadding(cmA, startA + aLen - 1, bLen - aLen);
    addPadding(cmB, startB + bLen - 1, aLen - bLen);
    addGutterTag(region, startA, startB);
    lineMapper.appendReplace(aLen, bLen);

    int endA = lineMapper.getLineA() - 1;
    int endB = lineMapper.getLineB() - 1;
    if (aLen > 0) {
      addDiffChunk(cmB, endA, aLen, bLen > 0);
    }
    if (bLen > 0) {
      addDiffChunk(cmA, endB, bLen, aLen > 0);
    }
  }

  private void addGutterTag(Region region, int startA, int startB) {
    if (region.a() == null) {
      scrollbar.insert(cmB, startB, region.b().length());
    } else if (region.b() == null) {
      scrollbar.delete(cmA, cmB, startA, region.a().length());
    } else {
      scrollbar.edit(cmB, startB, region.b().length());
    }
  }

  private void markEdit(CodeMirror cm, int startLine, JsArrayString lines, JsArray<Span> edits) {
    if (lines == null || edits == null) {
      return;
    }

    EditIterator iter = new EditIterator(lines, startLine);
    Configuration bg =
        Configuration.create()
            .set("className", SideBySideTable.style.intralineBg())
            .set("readOnly", true);

    Configuration diff =
        Configuration.create().set("className", SideBySideTable.style.diff()).set("readOnly", true);

    Pos last = Pos.create(0, 0);
    for (Span span : Natives.asList(edits)) {
      Pos from = iter.advance(span.skip());
      Pos to = iter.advance(span.mark());
      if (from.line() == last.line()) {
        getMarkers().add(cm.markText(last, from, bg));
      } else {
        getMarkers().add(cm.markText(Pos.create(from.line(), 0), from, bg));
      }
      getMarkers().add(cm.markText(from, to, diff));
      last = to;
      colorLines(
          cm, LineClassWhere.BACKGROUND, SideBySideTable.style.diff(), from.line(), to.line());
    }
  }

  /**
   * Insert a new padding div below the given line.
   *
   * @param cm parent CodeMirror to add extra space into.
   * @param line line to put the padding below.
   * @param len number of lines to pad. Padding is inserted only if {@code len >= 1}.
   */
  private void addPadding(CodeMirror cm, int line, int len) {
    if (0 < len) {
      Element pad = DOM.createDiv();
      pad.setClassName(SideBySideTable.style.padding());
      pad.setPropertyInt(DATA_LINES, len);
      pad.getStyle().setHeight(guessedLineHeightPx * len, Unit.PX);
      focusOnClick(pad, cm.side());
      paddingDivs.add(pad);
      padding.add(
          cm.addLineWidget(
              line == -1 ? 0 : line,
              pad,
              Configuration.create()
                  .set("coverGutter", true)
                  .set("noHScroll", true)
                  .set("above", line == -1)));
    }
  }

  private void addDiffChunk(CodeMirror cmToPad, int lineOnOther, int chunkSize, boolean edit) {
    chunks.add(
        new DiffChunkInfo(
            host.otherCm(cmToPad).side(), lineOnOther - chunkSize + 1, lineOnOther, edit));
  }

  @Override
  Runnable diffChunkNav(CodeMirror cm, Direction dir) {
    return () -> {
      int line = cm.extras().hasActiveLine() ? cm.getLineNumber(cm.extras().activeLine()) : 0;
      int res =
          Collections.binarySearch(
              chunks, new DiffChunkInfo(cm.side(), line, 0, false), getDiffChunkComparator());
      diffChunkNavHelper(chunks, host, res, dir);
    };
  }

  @Override
  int getCmLine(int line, DisplaySide side) {
    return line;
  }
}
