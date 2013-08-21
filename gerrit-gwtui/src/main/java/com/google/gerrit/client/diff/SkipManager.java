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

import java.util.Set;

/** Collapses common regions with {@link SideBySideSkipBar} for {@link SideBySide}
 *  and {@link Unified}. */
abstract class SkipManager {
  private final DiffScreen host;
  private final CommentManager commentManager;
  private Set<SkipBar> skipBars;
  private SkipBar line0;

  SkipManager(DiffScreen host, CommentManager commentManager) {
    this.host = host;
    this.commentManager = commentManager;
  }

  abstract void render(int context, DiffInfo diff);

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

  CommentManager getCommentManager() {
    return commentManager;
  }

  DiffScreen getDiffScreen() {
    return host;
  }

  SkipBar getLine0() {
    return line0;
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
