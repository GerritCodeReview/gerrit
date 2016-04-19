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
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo.DiffView;
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collapses common regions with {@link SkipBar} for {@link SideBySide}
 *  and {@link Unified}. */
class SkipManager {
  private final Set<SkipBar> skipBars;
  private final CommentManager commentManager;
  private final DiffScreen host;
  private SkipBar line0;
  private int lineA;
  private int lineB;

  SkipManager(DiffScreen host, CommentManager commentManager) {
    this.host = host;
    this.commentManager = commentManager;
    this.skipBars = new HashSet<>();
  }

  void render(int context, DiffInfo diff) {
    if (host.diffScreenType == DiffView.SIDE_BY_SIDE) {
      renderSideBySide(context, diff);
    } else {
      renderUnified(context, diff);
    }
  }

  private void renderSideBySide(int context, DiffInfo diff) {
    List<SkippedLine> skips = getSkippedLines(context, diff);

    if (!skips.isEmpty()) {
      CodeMirror cmA = host.getCmFromSide(DisplaySide.A);
      CodeMirror cmB = host.getCmFromSide(DisplaySide.B);

      for (SkippedLine skip : skips) {
        SkipBar barA = newSkipBar(cmA, DisplaySide.A, skip);
        SkipBar barB = newSkipBar(cmB, DisplaySide.B, skip);
        SkipBar.link(barA, barB);
        skipBars.add(barA);
        skipBars.add(barB);

        if (skip.getStartA() == 0 || skip.getStartB() == 0) {
          barA.upArrow.setVisible(false);
          barB.upArrow.setVisible(false);
          setLine0(barB);
        } else if (skip.getStartA() + skip.getSize() == getLineA()
            || skip.getStartB() + skip.getSize() == getLineB()) {
          barA.downArrow.setVisible(false);
          barB.downArrow.setVisible(false);
        }
      }
    }
  }

  private void renderUnified(int context, DiffInfo diff) {
    List<SkippedLine> skips = getSkippedLines(context, diff);

    if (!skips.isEmpty()) {
      CodeMirror cm = host.getCms()[0];

      for (SkippedLine skip : skips) {
        SkipBar bar = newSkipBar(cm, DisplaySide.A, skip);
        skipBars.add(bar);

        if (skip.getStartA() == 0 || skip.getStartB() == 0) {
          bar.upArrow.setVisible(false);
          setLine0(bar);
        } else if (skip.getStartA() + skip.getSize() == getLineA()
            || skip.getStartB() + skip.getSize() == getLineB()) {
          bar.downArrow.setVisible(false);
        }
      }
    }
  }

  private List<SkippedLine> getSkippedLines(int context, DiffInfo diff) {
    if (context == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
      return new ArrayList<>();
    }

    lineA = 0;
    lineB = 0;
    JsArray<Region> regions = diff.content();
    List<SkippedLine> skips = new ArrayList<>();
    for (int i = 0; i < regions.length(); i++) {
      Region current = regions.get(i);
      if (current.ab() != null || current.common() || current.skip() > 0) {
        int len = current.skip() > 0
            ? current.skip()
            : (current.ab() != null ? current.ab() : current.b()).length();
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
    return commentManager.splitSkips(context, skips);
  }

  private SkipBar newSkipBar(CodeMirror cm, DisplaySide side,
      SkippedLine skip) {
    int start = host.getCmLine(
        side == DisplaySide.A ? skip.getStartA() : skip.getStartB(), side);
    int end = start + skip.getSize() - 1;

    SkipBar bar = new SkipBar(this, cm);
    host.getDiffTable().add(bar);
    bar.collapse(start, end, true);
    return bar;
  }

  void ensureFirstLineIsVisible() {
    if (line0 != null) {
      line0.expandBefore(1);
      line0 = null;
    }
  }

  void removeAll() {
    if (!skipBars.isEmpty()) {
      for (SkipBar bar : skipBars) {
        bar.expandSideAll();
      }
      line0 = null;
    }
  }

  void remove(SkipBar a, SkipBar b) {
    skipBars.remove(a);
    skipBars.remove(b);
    if (getLine0() == a || getLine0() == b) {
      setLine0(null);
    }
  }

  int getLineA() {
    return lineA;
  }

  int getLineB() {
    return lineB;
  }

  SkipBar getLine0() {
    return line0;
  }

  void setLine0(SkipBar bar) {
    line0 = bar;
  }
}
