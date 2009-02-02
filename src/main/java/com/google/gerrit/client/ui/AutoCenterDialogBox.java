// Copyright 2008 Google Inc.
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

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.ui.DialogBox;

/** A DialogBox that automatically re-centers itself if the window changes */
public class AutoCenterDialogBox extends DialogBox {
  private WindowResizeListener recenter;

  public AutoCenterDialogBox() {
  }

  public AutoCenterDialogBox(final boolean autoHide) {
    super(autoHide);
  }

  public AutoCenterDialogBox(final boolean autoHide, final boolean modal) {
    super(autoHide, modal);
  }

  @Override
  public void show() {
    if (recenter == null) {
      recenter = new WindowResizeListener() {
        public void onWindowResized(final int width, final int height) {
          onResize(width, height);
        }
      };
      Window.addWindowResizeListener(recenter);
    }
    super.show();
  }

  @Override
  protected void onUnload() {
    if (recenter != null) {
      Window.removeWindowResizeListener(recenter);
      recenter = null;
    }
    super.onUnload();
  }

  protected void onResize(final int width, final int height) {
    if (isAttached()) {
      center();
    }
  }
}
