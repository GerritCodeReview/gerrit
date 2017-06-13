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

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.LineClassWhere;
import net.codemirror.lib.Pos;
import net.codemirror.lib.TextMarker;

/** Colors modified regions for {@link SideBySide} and {@link Unified}. */
abstract class ChunkManager {
  static final native void onClick(Element e, JavaScriptObject f) /*-{ e.onclick = f }-*/;

  final Scrollbar scrollbar;
  final LineMapper lineMapper;

  private List<TextMarker> markers;
  private List<Runnable> undo;

  ChunkManager(Scrollbar scrollbar) {
    this.scrollbar = scrollbar;
    this.lineMapper = new LineMapper();
  }

  abstract DiffChunkInfo getFirst();

  List<TextMarker> getMarkers() {
    return markers;
  }

  void reset() {
    lineMapper.reset();
    for (TextMarker m : markers) {
      m.clear();
    }
    for (Runnable r : undo) {
      r.run();
    }
  }

  abstract void render(DiffInfo diff);

  void render() {
    markers = new ArrayList<>();
    undo = new ArrayList<>();
  }

  void colorLines(CodeMirror cm, String color, int line, int cnt) {
    colorLines(cm, LineClassWhere.WRAP, color, line, line + cnt);
  }

  void colorLines(
      final CodeMirror cm,
      final LineClassWhere where,
      final String className,
      final int start,
      final int end) {
    if (start < end) {
      for (int line = start; line < end; line++) {
        cm.addLineClass(line, where, className);
      }
      undo.add(
          new Runnable() {
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

  void diffChunkNavHelper(
      List<? extends DiffChunkInfo> chunks, DiffScreen host, int res, Direction dir) {
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
    int cmLine = getCmLine(target.getStart(), target.getSide());
    targetCm.setCursor(Pos.create(cmLine));
    targetCm.focus();
    targetCm.scrollToY(
        targetCm.heightAtLine(cmLine, "local") - 0.5 * targetCm.scrollbarV().getClientHeight());
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
          int comp = lineMapper.lineOnOther(a.getSide(), a.getStart()).getLine() - b.getStart();
          return comp == 0 ? -1 : comp;
        } else {
          int comp = a.getStart() - lineMapper.lineOnOther(b.getSide(), b.getStart()).getLine();
          return comp == 0 ? 1 : comp;
        }
      }
    };
  }

  abstract int getCmLine(int line, DisplaySide side);
}
