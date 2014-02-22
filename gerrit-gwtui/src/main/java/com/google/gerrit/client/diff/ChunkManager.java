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

import static com.google.gerrit.client.diff.OverviewBar.MarkType.DELETE;
import static com.google.gerrit.client.diff.OverviewBar.MarkType.EDIT;
import static com.google.gerrit.client.diff.OverviewBar.MarkType.INSERT;

import com.google.gerrit.client.diff.DiffInfo.Region;
import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.DOM;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.LineWidget;
import net.codemirror.lib.TextMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Colors modified regions for {@link SideBySide2}. */
class ChunkManager {
  private final SideBySide2 host;
  private final CodeMirror cmA;
  private final CodeMirror cmB;
  private final OverviewBar sidePanel;
  private final LineMapper mapper;

  private List<DiffChunkInfo> chunks;
  private List<TextMarker> markers;
  private List<Runnable> undo;
  private List<LineWidget> padding;
  private boolean dark;

  ChunkManager(SideBySide2 host,
      CodeMirror cmA,
      CodeMirror cmB,
      OverviewBar sidePanel,
      boolean dark) {
    this.host = host;
    this.cmA = cmA;
    this.cmB = cmB;
    this.sidePanel = sidePanel;
    this.dark = dark;
    this.mapper = new LineMapper();
  }

  LineMapper getLineMapper() {
    return mapper;
  }

  DiffChunkInfo getFirst() {
    if (!chunks.isEmpty()) {
      for (DiffChunkInfo d : chunks) {
        if (d.getSide() == DisplaySide.B) {
          return d;
        }
      }
      return chunks.get(0);
    }
    return null;
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

    String diffColor = diff.meta_a() == null || diff.meta_b() == null
        ? (dark ? DiffTable.style.intralineBg_dark() : DiffTable.style.intralineBg())
        : (dark ? DiffTable.style.diff_dark() : DiffTable.style.diff());

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
        : (dark ? DiffTable.style.intralineBg_dark() : DiffTable.style.intralineBg());

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
      addDiffChunk(cmB, endB, endA, aLen, bLen > 0);
    }
    if (bLen > 0) {
      addDiffChunk(cmA, endA, endB, bLen, aLen > 0);
    }
  }

  private void addGutterTag(Region region, int startA, int startB) {
    if (region.a() == null) {
      sidePanel.add(cmB, startB, region.b().length(), INSERT);
    } else if (region.b() == null) {
      sidePanel.add(cmA, startA, region.a().length(), DELETE);
    } else {
      sidePanel.add(cmB, startB, region.b().length(), EDIT);
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
        .set("className", dark ? DiffTable.style.diff_dark() : DiffTable.style.diff())
        .set("readOnly", true);

    LineCharacter last = CodeMirror.pos(0, 0);
    for (Span span : Natives.asList(edits)) {
      LineCharacter from = iter.advance(span.skip());
      LineCharacter to = iter.advance(span.mark());
      if (from.getLine() == last.getLine()) {
        markers.add(cm.markText(last, from, bg));
      } else {
        markers.add(cm.markText(CodeMirror.pos(from.getLine(), 0), from, bg));
      }
      markers.add(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          (dark ? DiffTable.style.diff_dark() : DiffTable.style.diff()),
          from.getLine(), to.getLine());
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
  private void addPadding(CodeMirror cm, int line, int len) {
    if (0 < len) {
      // DiffTable adds 1px bottom padding to each line to preserve
      // sufficient space for underscores commonly appearing in code.
      // Padding should be 1em + 1px high for each line. Add within
      // the browser using height + padding-bottom.
      Element pad = DOM.createDiv();
      pad.setClassName(DiffTable.style.padding());
      pad.getStyle().setHeight(len, Unit.EM);
      pad.getStyle().setPaddingBottom(len, Unit.PX);
      padding.add(cm.addLineWidget(
        line == -1 ? 0 : line,
        pad,
        Configuration.create()
          .set("coverGutter", true)
          .set("noHScroll", true)
          .set("above", line == -1)));
    }
  }

  private void addDiffChunk(CodeMirror cmToPad, int lineToPad,
      int lineOnOther, int chunkSize, boolean edit) {
    chunks.add(new DiffChunkInfo(host.otherCm(cmToPad).side(),
        lineOnOther - chunkSize + 1, lineOnOther, edit));
  }

  Runnable diffChunkNav(final CodeMirror cm, final Direction dir) {
    return new Runnable() {
      @Override
      public void run() {
        int line = cm.hasActiveLine() ? cm.getLineNumber(cm.getActiveLine()) : 0;
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
        if (lookUp.isEdit() && lookUp.getSide() == DisplaySide.A) {
          res = res + (dir == Direction.PREV ? -1 : 1);
          if (res < 0 || chunks.size() <= res) {
            return;
          }
        }

        DiffChunkInfo target = chunks.get(res);
        CodeMirror targetCm = host.getCmFromSide(target.getSide());
        targetCm.setCursor(LineCharacter.create(target.getStart()));
        targetCm.focus();
        targetCm.scrollToY(
            targetCm.heightAtLine(target.getStart(), "local") -
            0.5 * cmB.getScrollbarV().getClientHeight());
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
        } else if (a.getSide() == DisplaySide.A) {
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
