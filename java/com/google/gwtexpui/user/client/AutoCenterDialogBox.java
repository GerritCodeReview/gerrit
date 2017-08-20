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

package com.google.gwtexpui.user.client;

import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DialogBox;

/** A DialogBox that automatically re-centers itself if the window changes */
public class AutoCenterDialogBox extends DialogBox {
  private HandlerRegistration recenter;

  public AutoCenterDialogBox() {
    this(false);
  }

  public AutoCenterDialogBox(boolean autoHide) {
    this(autoHide, true);
  }

  public AutoCenterDialogBox(boolean autoHide, boolean modal) {
    super(autoHide, modal);
  }

  @Override
  public void show() {
    if (recenter == null) {
      recenter =
          Window.addResizeHandler(
              new ResizeHandler() {
                @Override
                public void onResize(ResizeEvent event) {
                  final int w = event.getWidth();
                  final int h = event.getHeight();
                  AutoCenterDialogBox.this.onResize(w, h);
                }
              });
    }
    super.show();
  }

  @Override
  protected void onUnload() {
    if (recenter != null) {
      recenter.removeHandler();
      recenter = null;
    }
    super.onUnload();
  }

  /**
   * Invoked when the outer browser window resizes.
   *
   * <p>Subclasses may override (but should ensure they still call super.onResize) to implement
   * custom logic when a window resize occurs.
   *
   * @param width new browser window width
   * @param height new browser window height
   */
  protected void onResize(int width, int height) {
    if (isAttached()) {
      center();
    }
  }
}
