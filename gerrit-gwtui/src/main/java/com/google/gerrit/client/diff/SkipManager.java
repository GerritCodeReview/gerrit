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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Collapses common regions with {@link SideBySideSkipBar} for {@link SideBySide}
 *  and {@link Unified}. */
abstract class SkipManager {
  private Set<SkipBar> skipBars;
  private SkipBar line0;
  private CommentManager commentManager;
  private int lineA;
  private int lineB;

  SkipManager(CommentManager commentManager) {
    this.commentManager = commentManager;
  }

  abstract void render(int context, DiffInfo diff);

  List<SkippedLine> getSkippedLines(int context, DiffInfo diff) {
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

  SkipBar getLine0() {
    return line0;
  }

  int getLineA() {
    return lineA;
  }

  int getLineB() {
    return lineB;
  }

  void setLine0(SkipBar bar) {
    line0 = bar;
  }

  void setSkipBars(Set<SkipBar> bars) {
    skipBars = bars;
  }

  Set<SkipBar> getSkipBars() {
    return skipBars;
  }
}
