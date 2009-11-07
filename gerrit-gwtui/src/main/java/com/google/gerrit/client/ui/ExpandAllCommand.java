// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.Widget;

/** Expands all {@link CommentPanel} in a parent panel. */
public class ExpandAllCommand implements Command {
  private final Panel panel;
  protected final boolean open;

  public ExpandAllCommand(final Panel p, final boolean isOpen) {
    panel = p;
    open = isOpen;
  }

  public void execute() {
    for (final Widget w : panel) {
      if (w instanceof CommentPanel) {
        expand((CommentPanel) w);
      }
    }
  }

  protected void expand(final CommentPanel w) {
    w.setOpen(open);
  }
}
