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
import com.google.gwt.core.client.JsArray;

import net.codemirror.lib.CodeMirror;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Collapses common regions with {@link SideBySideSkipBar} for {@link Unified}. */
class UnifiedSkipManager extends SkipManager {
  UnifiedSkipManager(Unified host, UnifiedCommentManager commentManager) {
    super(host, commentManager);
  }

  @Override
  void render(int context, DiffInfo diff) {
    if (context == DiffPreferencesInfo.WHOLE_FILE_CONTEXT) {
      return;
    }

    JsArray<Region> regions = diff.content();
    List<SkippedLine> skips = new ArrayList<>();
    int lineA = 0, lineB = 0;
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
    skips = getCommentManager().splitSkips(context, skips);

    if (!skips.isEmpty()) {
      CodeMirror cm = ((Unified) getDiffScreen()).getCm();

      Set<SkipBar> skipBars = new HashSet<>();
      setSkipBars(skipBars);
      for (SkippedLine skip : skips) {
        UnifiedSkipBar bar = newSkipBar(cm, DisplaySide.A, skip);
        skipBars.add(bar);

        if (skip.getStartA() == 0 || skip.getStartB() == 0) {
          bar.upArrow.setVisible(false);
          setLine0(bar);
        } else if (skip.getStartA() + skip.getSize() == lineA
            || skip.getStartB() + skip.getSize() == lineB) {
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

  private UnifiedSkipBar newSkipBar(CodeMirror cm, DisplaySide side, SkippedLine skip) {
    int start = side == DisplaySide.A ? skip.getStartA() : skip.getStartB();
    int end = start + skip.getSize() - 1;

    UnifiedSkipBar bar = new UnifiedSkipBar(this, cm);
    getDiffScreen().getDiffTable().add(bar);
    bar.collapse(start, end, true);
    return bar;
  }
}
