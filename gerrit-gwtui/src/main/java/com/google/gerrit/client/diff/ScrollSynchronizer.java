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

import com.google.gwt.user.client.Timer;
import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.ScrollInfo;

class ScrollSynchronizer {
  private SideBySideTable diffTable;
  private LineMapper mapper;
  private ScrollCallback active;
  private ScrollCallback callbackA;
  private ScrollCallback callbackB;
  private CodeMirror cmB;
  private boolean autoHideDiffTableHeader;

  ScrollSynchronizer(SideBySideTable diffTable, CodeMirror cmA, CodeMirror cmB, LineMapper mapper) {
    this.diffTable = diffTable;
    this.mapper = mapper;
    this.cmB = cmB;

    callbackA = new ScrollCallback(cmA, cmB, DisplaySide.A);
    callbackB = new ScrollCallback(cmB, cmA, DisplaySide.B);
    cmA.on("scroll", callbackA);
    cmB.on("scroll", callbackB);
  }

  void setAutoHideDiffTableHeader(boolean autoHide) {
    if (autoHide) {
      updateDiffTableHeader(cmB.getScrollInfo());
    } else {
      diffTable.setHeaderVisible(true);
    }
    autoHideDiffTableHeader = autoHide;
  }

  void syncScroll(DisplaySide masterSide) {
    (masterSide == DisplaySide.A ? callbackA : callbackB).sync();
  }

  private void updateDiffTableHeader(ScrollInfo si) {
    if (si.top() == 0) {
      diffTable.setHeaderVisible(true);
    } else if (si.top() > 0.5 * si.clientHeight()) {
      diffTable.setHeaderVisible(false);
    }
  }

  class ScrollCallback implements Runnable {
    private final CodeMirror src;
    private final CodeMirror dst;
    private final DisplaySide srcSide;
    private final Timer fixup;
    private int state;

    ScrollCallback(CodeMirror src, CodeMirror dst, DisplaySide srcSide) {
      this.src = src;
      this.dst = dst;
      this.srcSide = srcSide;
      this.fixup =
          new Timer() {
            @Override
            public void run() {
              if (active == ScrollCallback.this) {
                fixup();
              }
            }
          };
    }

    void sync() {
      dst.scrollToY(align(src.getScrollInfo().top()));
    }

    @Override
    public void run() {
      if (active == null) {
        active = this;
        fixup.scheduleRepeating(20);
      }
      if (active == this) {
        ScrollInfo si = src.getScrollInfo();
        if (autoHideDiffTableHeader) {
          updateDiffTableHeader(si);
        }
        dst.scrollTo(si.left(), align(si.top()));
        state = 0;
      }
    }

    private void fixup() {
      switch (state) {
        case 0:
          state = 1;
          dst.scrollToY(align(src.getScrollInfo().top()));
          break;
        case 1:
          state = 2;
          break;
        case 2:
          active = null;
          fixup.cancel();
          break;
      }
    }

    private double align(double srcTop) {
      // Since CM doesn't always take the height of line widgets into
      // account when calculating scrollInfo when scrolling too fast (e.g.
      // throw scrolling), simply setting scrollTop to be the same doesn't
      // guarantee alignment.

      int line = src.lineAtHeight(srcTop, "local");
      if (line == 0) {
        // Padding for insert at start of file occurs above line 0,
        // and CM3 doesn't always compute heightAtLine correctly.
        return srcTop;
      }

      // Find a pair of lines that are aligned and near the top of
      // the viewport. Use that distance to correct the Y coordinate.
      LineMapper.AlignedPair p = mapper.align(srcSide, line);
      double sy = src.heightAtLine(p.src, "local");
      double dy = dst.heightAtLine(p.dst, "local");
      return Math.max(0, dy + (srcTop - sy));
    }
  }
}
