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

import com.google.gerrit.client.diff.DiffInfo.Span;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Configuration;
import net.codemirror.lib.LineCharacter;
import net.codemirror.lib.TextMarker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Colors modified regions for {@link SideBySide2}. */
abstract class ChunkManager {

  private final DiffScreen host;
  private final OverviewBar sidePanel;
  private final LineMapper mapper;

  private List<DiffChunkInfo> chunks;
  private List<TextMarker> markers;
  private List<Runnable> undo;

  ChunkManager(DiffScreen host,
      OverviewBar sidePanel) {
    this.host = host;
    this.sidePanel = sidePanel;
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
  }

  void render(DiffInfo diff) {
    chunks = new ArrayList<>();
    markers = new ArrayList<>();
    undo = new ArrayList<>();
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
        markers.add(cm.markText(last, from, bg));
      } else {
        markers.add(cm.markText(CodeMirror.pos(from.getLine(), 0), from, bg));
      }
      markers.add(cm.markText(from, to, diff));
      last = to;
      colorLines(cm, LineClassWhere.BACKGROUND,
          SideBySideTable2.style.diff(),
          from.getLine(), to.getLine());
    }
  }

  void colorLines(CodeMirror cm, String color, int line, int cnt) {
    colorLines(cm, LineClassWhere.WRAP, color, line, line + cnt);
  }

  void colorLines(final CodeMirror cm, final LineClassWhere where,
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

  abstract Runnable diffChunkNav(final CodeMirror cm, final Direction dir);

  void diffChunkNavHelper(CodeMirror cm, int res, Direction dir) {
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
    cm.setCursor(LineCharacter.create(target.getStart()));
    cm.focus();
    cm.scrollToY(
        cm.heightAtLine(target.getStart(), "local") -
        0.5 * cm.getScrollbarV().getClientHeight());
  }

  Comparator<DiffChunkInfo> getDiffChunkComparator() {
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

  LineMapper getMapper() {
    return mapper;
  }

  DiffScreen getDiffScreen() {
    return host;
  }

  OverviewBar getOverviewBar() {
    return sidePanel;
  }

  List<DiffChunkInfo> getChunks() {
    return Collections.unmodifiableList(chunks);
  }

  void addDiffChunk(DiffChunkInfo info) {
    chunks.add(info);
  }

  void addTextMarker(TextMarker marker) {
    markers.add(marker);
  }
}
