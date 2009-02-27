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

package com.google.gwtexpui.user.client;

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

  /**
   * Invoked when the outer browser window resizes.
   * <p>
   * Subclasses may override (but should ensure they still call super.onResize)
   * to implement custom logic when a window resize occurs.
   * 
   * @param width new browser window width
   * @param height new browser window height
   */
  protected void onResize(final int width, final int height) {
    if (isAttached()) {
      center();
    }
  }
}
