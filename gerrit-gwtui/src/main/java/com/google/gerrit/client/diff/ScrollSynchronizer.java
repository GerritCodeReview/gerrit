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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.diff.LineMapper.LineOnOtherInfo;
import com.google.gwt.user.client.Timer;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.CodeMirror.Viewport;
import net.codemirror.lib.ScrollInfo;

class ScrollSynchronizer {
  private DiffTable diffTable;
  private LineMapper mapper;
  private ScrollCallback active;

  void init(DiffTable diffTable,
      CodeMirror cmA, CodeMirror cmB,
      LineMapper mapper) {
    this.diffTable = diffTable;
    this.mapper = mapper;

    cmA.on("scroll", new ScrollCallback(cmA, cmB, DisplaySide.A));
    cmB.on("scroll", new ScrollCallback(cmB, cmA, DisplaySide.B));
  }

  private void updateScreenHeader(ScrollInfo si) {
    if (si.getTop() == 0 && !Gerrit.isHeaderVisible()) {
      diffTable.setHeaderVisible(true);
    } else if (si.getTop() > 0.5 * si.getClientHeight()
        && Gerrit.isHeaderVisible()) {
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
      this.fixup = new Timer() {
        @Override
        public void run() {
          if (active == ScrollCallback.this) {
            fixup();
          }
        }
      };
    }

    @Override
    public void run() {
      if (active == null) {
        active = this;
        fixup.scheduleRepeating(20);
      }
      if (active == this) {
        ScrollInfo si = src.getScrollInfo();
        updateScreenHeader(si);
        dst.scrollTo(si.getLeft(), si.getTop());
        state = 0;
      }
    }

    private void fixup() {
      switch (state) {
        case 0:
          state = 1;
          break;
        case 1:
          state = 2;
          return;
        case 2:
          active = null;
          fixup.cancel();
          return;
      }

      // Since CM doesn't always take the height of line widgets into
      // account when calculating scrollInfo when scrolling too fast (e.g.
      // throw scrolling), simply setting scrollTop to be the same doesn't
      // guarantee alignment.
      //
      // Iterate over the viewport to find the first line that isn't part of
      // an insertion or deletion gap, for which isAligned() will be true.
      // We then manually examine if the lines that should be aligned are at
      // the same height. If not, perform additional scrolling.
      Viewport fromTo = src.getViewport();
      for (int line = fromTo.getFrom(); line <= fromTo.getTo(); line++) {
        LineOnOtherInfo info = mapper.lineOnOther(srcSide, line);
        if (info.isAligned()) {
          double sy = src.heightAtLine(line);
          double dy = dst.heightAtLine(info.getLine());
          if (Math.abs(dy - sy) >= 1) {
            dst.scrollToY(dst.getScrollInfo().getTop() + (dy - sy));
          }
          break;
        }
      }
    }
  }
}
