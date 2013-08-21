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

/** Collapses common regions with {@link UnifiedSkipBar} for {@link Unified}. */
class UnifiedSkipManager extends SkipManager {
  private Unified host;

  UnifiedSkipManager(Unified host, UnifiedCommentManager commentManager) {
    super(commentManager);
    this.host = host;
  }

  @Override
  void render(int context, DiffInfo diff) {
    List<SkippedLine> skips = getSkippedLines(context, diff);

    if (!skips.isEmpty()) {
      CodeMirror cm = host.getCm();

      Set<SkipBar> skipBars = new HashSet<>();
      setSkipBars(skipBars);
      for (SkippedLine skip : skips) {
        UnifiedSkipBar bar = newSkipBar(cm, skip);
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

  void remove(UnifiedSkipBar bar) {
    Set<SkipBar> skipBars = getSkipBars();
    skipBars.remove(bar);
    if (getLine0() == bar) {
      setLine0(null);
    }
    if (skipBars.isEmpty()) {
      setSkipBars(null);
    }
  }

  private UnifiedSkipBar newSkipBar(CodeMirror cm, SkippedLine skip) {
    int start = host.getCmLine(skip.getStartA(), DisplaySide.A);
    int end = start + skip.getSize() - 1;

    UnifiedSkipBar bar = new UnifiedSkipBar(this, cm);
    host.getDiffTable().add(bar);
    bar.collapse(start, end, true);
    return bar;
  }
}
