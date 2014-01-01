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
import com.google.gerrit.client.patches.SkippedLine;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/** Collapses common regions with {@link SkipBar} for {@link SideBySide2}. */
class SkipManager {
  private final SideBySide2 host;
  private final CommentManager commentManager;
  private SortedMap<Integer, SkipBar> sideA;
  private SortedMap<Integer, SkipBar> sideB;

  SkipManager(SideBySide2 host, CommentManager commentManager) {
    this.host = host;
    this.commentManager = commentManager;
  }

  void render(int context, DiffInfo diff) {
    if (context == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
      return;
    }

    JsArray<Region> regions = diff.content();
    List<SkippedLine> skips = new ArrayList<SkippedLine>();
    int lineA = 0, lineB = 0;
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      if (current.ab() != null) {
        int len = current.ab().length();
        if (i == 0 && len > context + 1) {
          skips.add(new SkippedLine(0, 0, len - context));
        } else if (i == regions.length() - 1 && len > context + 1) {
          skips.add(new SkippedLine(lineA + context, lineB + context,
              len - context));
        } else if (len > 2 * context + 1) {
          skips.add(new SkippedLine(lineA + context, lineB + context,
              len - 2 * context));
        }
        lineA += len;
        lineB += len;
      } else {
        lineA += current.a() != null ? current.a().length() : 0;
        lineB += current.b() != null ? current.b().length() : 0;
      }
    }
    skips = commentManager.splitSkips(context, skips);

    if (!skips.isEmpty()) {
      CodeMirror cmA = host.getCmFromSide(DisplaySide.A);
      CodeMirror cmB = host.getCmFromSide(DisplaySide.B);

      sideA = new TreeMap<Integer, SkipBar>();
      sideB = new TreeMap<Integer, SkipBar>();

      for (SkippedLine skip : skips) {
        SkipBar barA = newSkipBar(cmA, DisplaySide.A, skip);
        SkipBar barB = newSkipBar(cmB, DisplaySide.B, skip);
        SkipBar.link(barA, barB);
        sideA.put(barA.getStart(), barA);
        sideB.put(barB.getStart(), barB);

        if (skip.getStartA() == 0 || skip.getStartB() == 0) {
          barA.upArrow.setVisible(false);
          barB.upArrow.setVisible(false);
        } else if (skip.getStartA() + skip.getSize() == lineA
            || skip.getStartB() + skip.getSize() == lineB) {
          barA.downArrow.setVisible(false);
          barB.downArrow.setVisible(false);
        }
      }
    }
  }

  void ensureFirstLineIsVisible() {
    if (sideB != null) {
      SkipBar line0 = sideB.get(0);
      if (line0 != null) {
        line0.expandBefore(1);
      }
    }
  }

  void ensureLineVisible(CodeMirror cm, int line) {
    SortedMap<Integer, SkipBar> map = map(cm.side());
    SortedMap<Integer, SkipBar> m = map.headMap(line + 1);
    if (!m.isEmpty()) {
      SkipBar bar = m.get(m.lastKey());
      if (bar.contains(line)) {
        bar.ensureLineVisible(line, host.getPrefs().context());
      }
    }
  }

  void removeAll() {
    if (sideB != null) {
      for (SkipBar bar : sideB.values()) {
        bar.expandAll();
      }
      sideA = null;
      sideB = null;
    }
  }

  void remove(SkipBar a, SkipBar b) {
    map(a.getSide()).remove(a.getStart());
    map(b.getSide()).remove(b.getStart());

    if (sideB.isEmpty()) {
      sideA = null;
      sideB = null;
    }
  }

  void move(SkipBar bar, int oldLine, int newLine) {
    SortedMap<Integer, SkipBar> map = map(bar.getSide());
    map.remove(oldLine);
    map.put(newLine, bar);
  }

  void skip(DisplaySide side, int start, int end) {
    int size = end - start;
    int startA, startB;
    if (side == DisplaySide.A) {
      startA = start;
      startB = host.lineOnOther(side, start).getLine();
    } else {
      startA = host.lineOnOther(side, start).getLine();
      startB = start;
    }

    SkippedLine skip = new SkippedLine(startA, startB, size);
    CodeMirror cmA = host.getCmFromSide(DisplaySide.A);
    CodeMirror cmB = host.getCmFromSide(DisplaySide.B);

    SkipBar barA = newSkipBar(cmA, DisplaySide.A, skip);
    SkipBar barB = newSkipBar(cmB, DisplaySide.B, skip);
    SkipBar.link(barA, barB);
    sideA.put(barA.getStart(), barA);
    sideB.put(barB.getStart(), barB);
  }

  private SkipBar newSkipBar(CodeMirror cm, DisplaySide side, SkippedLine skip) {
    int start = side == DisplaySide.A ? skip.getStartA() : skip.getStartB();
    int end = start + skip.getSize() - 1;

    SkipBar bar = new SkipBar(this, cm);
    host.diffTable.add(bar);
    bar.collapse(start, end, true);
    return bar;
  }

  private SortedMap<Integer, SkipBar> map(DisplaySide side) {
    return side == DisplaySide.A ? sideA : sideB;
  }
}
