// Copyright (C) 2014 The Android Open Source Project
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

/**
 * LineWidget attached to a CodeMirror container.
 *
 * <p>When a comment is placed on a line a CommentWidget is created. The group tracks all comment
 * boxes on a line in unified diff view.
 */
class UnifiedCommentGroup extends CommentGroup {
  UnifiedCommentGroup(UnifiedCommentManager manager, CodeMirror cm, DisplaySide side, int line) {
    super(manager, cm, side, line);
  }

  @Override
  void remove(DraftBox box) {
    super.remove(box);

    if (0 < getBoxCount()) {
      resize();
    } else {
      detach();
    }
  }

  @Override
  void init(DiffTable parent) {
    if (getLineWidget() == null) {
      attach(parent);
    }
  }

  @Override
  void handleRedraw() {
    getLineWidget()
        .onRedraw(
            new Runnable() {
              @Override
              public void run() {
                if (canComputeHeight()) {
                  if (getResizeTimer() != null) {
                    getResizeTimer().cancel();
                    setResizeTimer(null);
                  }
                  reportHeightChange();
                } else if (getResizeTimer() == null) {
                  setResizeTimer(
                      new Timer() {
                        @Override
                        public void run() {
                          if (canComputeHeight()) {
                            cancel();
                            setResizeTimer(null);
                            reportHeightChange();
                          }
                        }
                      });
                  getResizeTimer().scheduleRepeating(5);
                }
              }
            });
  }

  @Override
  void resize() {
    if (getLineWidget() != null) {
      reportHeightChange();
    }
  }

  private void reportHeightChange() {
    getLineWidget().changed();
    updateSelection();
  }
}
