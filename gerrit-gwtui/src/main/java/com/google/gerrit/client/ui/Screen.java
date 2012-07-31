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

import com.google.gerrit.client.Gerrit;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.View;

/**
  *  A Screen layout with a header and a body.
  *
  * The header is mainly a text title, but it can be decorated
  * in the West, the East, and the FarEast by any Widget.  The
  * West and East decorations will surround the text on the
  * left and right respectively, and the FarEast will be right
  * justified to the right edge of the screen.  The East
  * decoration will expand to take up any extra space.
  */
public abstract class Screen extends View {
  private final FlowPanel body;
  private Label headerText;
  private String token;
  private boolean requiresSignIn;
  private String windowTitle;

  protected Screen() {
    initWidget(body = new FlowPanel());
    setStyleName(Gerrit.RESOURCES.css().screen());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    onInitUI();
  }

  protected void onInitUI() {
  }

  public void registerKeys() {
  }

  protected void setWindowTitle(final String text) {
    windowTitle = text;
    Gerrit.setWindowTitle(this, text);
  }

  protected void setPageTitle(String text) {
    if (text.isEmpty()) {
      removePageTitle();
    } else if (headerText != null) {
      headerText.setText(text);
    } else {
      body.insert(headerText = new Label(), 0);
      headerText.setStyleName(Gerrit.RESOURCES.css().screenHeader());
    }
    if (windowTitle == null) {
      setWindowTitle(text);
    }
  }

  protected void removePageTitle() {
    if (headerText != null) {
      headerText.removeFromParent();
      headerText = null;
    }
  }

  protected void add(final Widget w) {
    body.add(w);
  }

  /** Get the history token for this screen. */
  public String getToken() {
    return token;
  }

  /** Set the history token for this screen. */
  public void setToken(final String t) {
    assert t != null && !t.isEmpty();
    token = t;

    if (isCurrentView()) {
      Gerrit.updateImpl(token);
    }
  }

  /**
   * If this view can display the given token, update it.
   *
   * @param newToken token the UI wants to show.
   * @return true if this view can show the token immediately, false if not.
   */
  public boolean displayToken(String newToken) {
    return false;
  }

  /** Set whether or not {@link Gerrit#isSignedIn()} must be true. */
  public final void setRequiresSignIn(final boolean b) {
    requiresSignIn = b;
  }

  /** Does {@link Gerrit#isSignedIn()} have to be true to be on this screen? */
  public final boolean isRequiresSignIn() {
    return requiresSignIn;
  }

  public void onShowView() {
    if (windowTitle != null) {
      Gerrit.setWindowTitle(this, windowTitle);
    }
    Gerrit.updateMenus(this);
    registerKeys();
  }
}
