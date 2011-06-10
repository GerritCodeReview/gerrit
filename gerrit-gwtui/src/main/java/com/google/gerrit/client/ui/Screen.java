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
import com.google.gerrit.common.PageLinks;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.View;

public abstract class Screen extends View {
  private FlowPanel header;
  private InlineLabel headerText;
  private FlowPanel body;
  private String token;
  private boolean requiresSignIn;
  private String windowTitle;

  protected Screen() {
    initWidget(new FlowPanel());
    setStyleName(Gerrit.RESOURCES.css().screen());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    if (header == null) {
      onInitUI();
    }
  }

  public void registerKeys() {
  }

  protected void onInitUI() {
    final FlowPanel me = (FlowPanel) getWidget();
    me.add(header = new FlowPanel());
    me.add(body = new FlowPanel());

    header.setStyleName(Gerrit.RESOURCES.css().screenHeader());
    header.add(headerText = new InlineLabel());
  }

  protected void setWindowTitle(final String text) {
    windowTitle = text;
    Gerrit.setWindowTitle(this, text);
  }

  protected void setPageTitle(final String text) {
    final String old = headerText.getText();
    if (text.isEmpty()) {
      header.setVisible(false);
    } else {
      headerText.setText(text);
      header.setVisible(true);
    }
    if (windowTitle == null || windowTitle == old) {
      setWindowTitle(text);
    }
  }

  protected void insertTitleWidget(final Widget w) {
    header.insert(w, 0);
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

  /** Invoked if this screen is the current screen and the user signs out. */
  public void onSignOut() {
    if (isRequiresSignIn()) {
      History.newItem(PageLinks.toChangeQuery("status:open"));
    }
  }

  public void onShowView() {
    if (windowTitle != null) {
      Gerrit.setWindowTitle(this, windowTitle);
    }
    Gerrit.updateMenus(this);
    registerKeys();
  }
}
