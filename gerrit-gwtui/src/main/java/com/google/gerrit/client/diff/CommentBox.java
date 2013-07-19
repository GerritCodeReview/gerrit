//Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.client.changes.CommentInfo;
import com.google.gerrit.client.diff.PaddingManager.PaddingWidgetWrapper;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.user.client.ui.Composite;

/** An HtmlPanel for displaying a comment */
abstract class CommentBox extends Composite {
  static {
    Resources.I.style().ensureInjected();
  }

  private PaddingManager widgetManager;
  private PaddingWidgetWrapper selfWidgetWrapper;

  void resizePaddingWidget() {
    if (!getCommentInfo().has_line()) {
      return;
    }
    Scheduler.get().scheduleDeferred(new ScheduledCommand() {
      @Override
      public void execute() {
        assert selfWidgetWrapper != null;
        selfWidgetWrapper.getWidget().changed();
        assert widgetManager != null;
        widgetManager.resizePaddingWidget();
      }
    });
  }

  abstract CommentInfo getCommentInfo();
  abstract boolean isOpen();

  void setOpen(boolean open) {
    resizePaddingWidget();
  }

  PaddingManager getPaddingManager() {
    return widgetManager;
  }

  void setPaddingManager(PaddingManager manager) {
    widgetManager = manager;
  }

  void setSelfWidgetWrapper(PaddingWidgetWrapper wrapper) {
    selfWidgetWrapper = wrapper;
  }

  PaddingWidgetWrapper getSelfWidgetWrapper() {
    return selfWidgetWrapper;
  }
}
