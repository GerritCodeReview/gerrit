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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collapses common regions with {@link SkipBar} for {@link SideBySide}. */
class SkipManager {
  private final SideBySide host;
  private final CommentManager commentManager;
  private Set<SkipBar> skipBars;
  private SkipBar line0;

  SkipManager(SideBySide host, CommentManager commentManager) {
    this.host = host;
    this.commentManager = commentManager;
  }

  void render(int context, DiffInfo diff) {
    if (context == AccountDiffPreference.WHOLE_FILE_CONTEXT) {
      return;
    }

    JsArray<Region> regions = diff.content();
    List<SkippedLine> skips = new ArrayList<>();
    int lineA = 0;
    int lineB = 0;
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
    skips = commentManager.splitSkips(context, skips);

    if (!skips.isEmpty()) {
      CodeMirror cmA = host.getCmFromSide(DisplaySide.A);
      CodeMirror cmB = host.getCmFromSide(DisplaySide.B);

      skipBars = new HashSet<>();
      for (SkippedLine skip : skips) {
        SkipBar barA = newSkipBar(cmA, DisplaySide.A, skip);
        SkipBar barB = newSkipBar(cmB, DisplaySide.B, skip);
        SkipBar.link(barA, barB);
        skipBars.add(barA);
        skipBars.add(barB);

        if (skip.getStartA() == 0 || skip.getStartB() == 0) {
          barA.upArrow.setVisible(false);
          barB.upArrow.setVisible(false);
          line0 = barB;
        } else if (skip.getStartA() + skip.getSize() == lineA
            || skip.getStartB() + skip.getSize() == lineB) {
          barA.downArrow.setVisible(false);
          barB.downArrow.setVisible(false);
        }
      }
    }
  }

  void ensureFirstLineIsVisible() {
    if (line0 != null) {
      line0.expandBefore(1);
      line0 = null;
    }
  }

  void removeAll() {
    if (skipBars != null) {
      for (SkipBar bar : skipBars) {
        bar.expandSideAll();
      }
      skipBars = null;
      line0 = null;
    }
  }

  void remove(SkipBar a, SkipBar b) {
    skipBars.remove(a);
    skipBars.remove(b);
    if (line0 == a || line0 == b) {
      line0 = null;
    }
    if (skipBars.isEmpty()) {
      skipBars = null;
    }
  }

  private SkipBar newSkipBar(CodeMirror cm, DisplaySide side, SkippedLine skip) {
    int start = side == DisplaySide.A ? skip.getStartA() : skip.getStartB();
    int end = start + skip.getSize() - 1;

    SkipBar bar = new SkipBar(this, cm);
    host.diffTable.add(bar);
    bar.collapse(start, end, true);
    return bar;
  }
}
