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

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Colors modified regions for {@link SideBySide}. */
class ChunkManager {
  private static final String DATA_LINES = "_cs2h";
  private static double guessedLineHeightPx = 15;
  private static final JavaScriptObject focusA = initOnClick(A);
  private static final JavaScriptObject focusB = initOnClick(B);
  private static final native JavaScriptObject initOnClick(DisplaySide s) /*-{
    return $entry(function(e){
      @com.google.gerrit.client.diff.ChunkManager::focus(
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

  private static final native void onClick(Element e, JavaScriptObject f)
  /*-{ e.onclick = f }-*/;

  private final SideBySide host;
  private final CodeMirror cmA;
  private final CodeMirror cmB;
  private final Scrollbar scrollbar;
  private final LineMapper mapper;

  private List<DiffChunkInfo> chunks;
  private List<TextMarker> markers;
  private List<Runnable> undo;
  private List<LineWidget> padding;
  private List<Element> paddingDivs;

  ChunkManager(SideBySide host,
      CodeMirror cmA,
      CodeMirror cmB,
      Scrollbar scrollbar) {
    this.host = host;
    this.cmA = cmA;
    this.cmB = cmB;
    this.scrollbar = scrollbar;
    this.mapper = new LineMapper();
  }

  LineMapper getLineMapper() {
    return mapper;
  }

  DiffChunkInfo getFirst() {
    return !chunks.isEmpty() ? chunks.get(0) : null;
  }

  void reset() {
    mapper.reset();
    for (TextMarker m : markers) {
      m.clear();
    }
    for (Runnable r : undo) {
      r.run();
    }
    for (LineWidget w : padding) {
      w.clear();
    }
  }

  void render(DiffInfo diff) {
    chunks = new ArrayList<>();
    markers = new ArrayList<>();
    undo = new ArrayList<>();
    padding = new ArrayList<>();
    paddingDivs = new ArrayList<>();

    String diffColor = diff.meta_a() == null || diff.meta_b() == null
        ? DiffTable.style.intralineBg()
        : DiffTable.style.diff();

    for (Region current : Natives.asList(diff.content())) {
      if (current.ab() != null) {
        mapper.appendCommon(current.ab().length());
      } else if (current.skip() > 0) {
        mapper.appendCommon(current.skip());
      } else if (current.common()) {
        mapper.appendCommon(current.b().length());
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
    int startA = mapper.getLineA();
    int startB = mapper.getLineB();

    JsArrayString a = region.a();
    JsArrayString b = region.b();
    int aLen = a != null ? a.length() : 0;
    int bLen = b != null ? b.length() : 0;

    String color = a == null || b == null
        ? diffColor
        : DiffTable.style.intralineBg();

    colorLines(cmA, color, startA, aLen);
    colorLines(cmB, color, startB, bLen);
    markEdit(cmA, startA, a, region.edit_a());
    markEdit(cmB, startB, b, region.edit_b());
    addPadding(cmA, startA + aLen - 1, bLen - aLen);
    addPadding(cmB, startB + bLen - 1, aLen - bLen);
    addGutterTag(region, startA, startB);
    mapper.appendReplace(aLen, bLen);

    int endA = mapper.getLineA() - 1;
    int endB = mapper.getLineB() - 1;
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

  private void markEdit(CodeMirror cm, int startLine,
      JsArrayString lines, JsArray<Span> edits) {
    if (lines == null || edits == null) {
      return;
    }

    EditIterator iter = new EditIterator(lines, startLine);
    Configuration bg = Configuration.create()
        .set("className", DiffTable.style.intralineBg())
        .set("readOnly", true);

    Configuration diff = Configuration.create()
        .set("className", DiffTable.style.diff())
        .set("readOnly", true);

    Pos last = Pos.create(0, 0);
    for (Span span : Natives.asList(edits)) {
      Pos from = iter.advance(span.skip());
      Pos to = iter.advance(span.mark());
      if (from.line() == last.line()) {
        markers.add(cm.markText(last, from, bg));
      } else {
        markers.add(cm.markText(Pos.create(from.line(), 0), from, bg));
      }
      markers.add(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          DiffTable.style.diff(),
          from.line(), to.line());
    }
  }

  private void colorLines(CodeMirror cm, String color, int line, int cnt) {
    colorLines(cm, LineClassWhere.WRAP, color, line, line + cnt);
  }

  private void colorLines(final CodeMirror cm, final LineClassWhere where,
      final String className, final int start, final int end) {
    if (start < end) {
      for (int line = start; line < end; line++) {
        cm.addLineClass(line, where, className);
      }
      undo.add(new Runnable() {
        @Override
        public void run() {
          for (int line = start; line < end; line++) {
            cm.removeLineClass(line, where, className);
          }
        }
      });
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
  private void addPadding(CodeMirror cm, int line, final int len) {
    if (0 < len) {
      Element pad = DOM.createDiv();
      pad.setClassName(DiffTable.style.padding());
      pad.setPropertyInt(DATA_LINES, len);
      pad.getStyle().setHeight(guessedLineHeightPx * len, Unit.PX);
      focusOnClick(pad, cm.side());
      paddingDivs.add(pad);
      padding.add(cm.addLineWidget(
        line == -1 ? 0 : line,
        pad,
        Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true)
          .set("above", line == -1)));
    }
  }

  private void addDiffChunk(CodeMirror cmToPad, int lineOnOther,
      int chunkSize, boolean edit) {
    chunks.add(new DiffChunkInfo(host.otherCm(cmToPad).side(),
        lineOnOther - chunkSize + 1, lineOnOther, edit));
  }

  Runnable diffChunkNav(final CodeMirror cm, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.extras().hasActiveLine()
            ? cm.getLineNumber(cm.extras().activeLine())
            : 0;
        int res = Collections.binarySearch(
                chunks,
                new DiffChunkInfo(cm.side(), line, 0, false),
                getDiffChunkComparator());
        if (res < 0) {
          res = -res - (dir == Direction.PREV ? 1 : 2);
        }
        res = res + (dir == Direction.PREV ? -1 : 1);
        if (res < 0 || chunks.size() <= res) {
          return;
        }

        DiffChunkInfo lookUp = chunks.get(res);
        // If edit, skip the deletion chunk and set focus on the insertion one.
        if (lookUp.isEdit() && lookUp.getSide() == A) {
          res = res + (dir == Direction.PREV ? -1 : 1);
          if (res < 0 || chunks.size() <= res) {
            return;
          }
        }

        DiffChunkInfo target = chunks.get(res);
        CodeMirror targetCm = host.getCmFromSide(target.getSide());
        targetCm.setCursor(Pos.create(target.getStart(), 0));
        targetCm.focus();
        targetCm.scrollToY(
            targetCm.heightAtLine(target.getStart(), "local") -
            0.5 * cmB.scrollbarV().getClientHeight());
      }
    };
  }

  private Comparator<DiffChunkInfo> getDiffChunkComparator() {
    // Chunks are ordered by their starting line. If it's a deletion,
    // use its corresponding line on the revision side for comparison.
    // In the edit case, put the deletion chunk right before the
    // insertion chunk. This placement guarantees well-ordering.
    return new Comparator<DiffChunkInfo>() {
      @Override
      public int compare(DiffChunkInfo a, DiffChunkInfo b) {
        if (a.getSide() == b.getSide()) {
          return a.getStart() - b.getStart();
        } else if (a.getSide() == A) {
          int comp = mapper.lineOnOther(a.getSide(), a.getStart())
              .getLine() - b.getStart();
          return comp == 0 ? -1 : comp;
        } else {
          int comp = a.getStart() -
              mapper.lineOnOther(b.getSide(), b.getStart()).getLine();
          return comp == 0 ? 1 : comp;
        }
      }
    };
  }

  DiffChunkInfo getDiffChunk(DisplaySide side, int line) {
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
  }
}
