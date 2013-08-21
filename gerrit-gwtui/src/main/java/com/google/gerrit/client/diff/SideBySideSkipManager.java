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

import com.google.gerrit.client.patches.SkippedLine;

import net.codemirror.lib.CodeMirror;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collapses common regions with {@link SideBySideSkipBar} for {@link SideBySide}. */
class SideBySideSkipManager extends SkipManager {
  private SideBySide host;

  SideBySideSkipManager(SideBySide host, SideBySideCommentManager commentManager) {
    super(commentManager);
    this.host = host;
  }

  @Override
  void render(int context, DiffInfo diff) {
    List<SkippedLine> skips = getSkippedLines(context, diff);

    if (!skips.isEmpty()) {
      CodeMirror cmA = host.getCmFromSide(DisplaySide.A);
      CodeMirror cmB = host.getCmFromSide(DisplaySide.B);

      Set<SkipBar> skipBars = new HashSet<>();
      setSkipBars(skipBars);
      for (SkippedLine skip : skips) {
        SideBySideSkipBar barA = newSkipBar(cmA, DisplaySide.A, skip);
        SideBySideSkipBar barB = newSkipBar(cmB, DisplaySide.B, skip);
        SideBySideSkipBar.link(barA, barB);
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

  void remove(SideBySideSkipBar a, SideBySideSkipBar b) {
    Set<SkipBar> skipBars = getSkipBars();
    skipBars.remove(a);
    skipBars.remove(b);
    if (getLine0() == a || getLine0() == b) {
      setLine0(null);
    }
    if (skipBars.isEmpty()) {
      setSkipBars(null);
    }
  }

  private SideBySideSkipBar newSkipBar(CodeMirror cm, DisplaySide side, SkippedLine skip) {
    int start = side == DisplaySide.A ? skip.getStartA() : skip.getStartB();
    int end = start + skip.getSize() - 1;

    SideBySideSkipBar bar = new SideBySideSkipBar(this, cm);
    host.getDiffTable().add(bar);
    bar.collapse(start, end, true);
    return bar;
  }
}
