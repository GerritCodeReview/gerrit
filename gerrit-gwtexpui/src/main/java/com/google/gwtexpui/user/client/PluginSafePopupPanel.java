// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.PopupPanel;

/**
 * A PopupPanel that can appear over Flash movies and Java applets.
 * <p>
 * Some browsers have issues with placing a &lt;div&gt; (such as that used by
 * the PopupPanel implementation) over top of native UI such as that used by the
 * Flash plugin. Often the native UI leaks over top of the &lt;div&gt;, which is
 * not the desired behavior for a dialog box.
 * <p>
 * This implementation hides the native resources by setting their display
 * property to 'none' when the dialog is shown, and restores them back to their
 * prior setting when the dialog is hidden.
 * */
public class PluginSafePopupPanel extends PopupPanel {
  private final PluginSafeDialogBoxImpl impl =
      GWT.create(PluginSafeDialogBoxImpl.class);

  public PluginSafePopupPanel() {
    this(false);
  }

  public PluginSafePopupPanel(final boolean autoHide) {
    this(autoHide, true);
  }

  public PluginSafePopupPanel(final boolean autoHide, final boolean modal) {
    super(autoHide, modal);
  }

  @Override
  public void setVisible(final boolean show) {
    impl.visible(show);
    super.setVisible(show);
  }

  @Override
  public void show() {
    impl.visible(true);
    super.show();
  }

  @Override
  public void hide(final boolean autoClosed) {
    impl.visible(false);
    super.hide(autoClosed);
  }
}
