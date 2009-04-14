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
import com.google.gerrit.client.Link;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.View;

public class Screen extends View {
  private final FlowPanel header;
  private final InlineLabel headerText;
  private final FlowPanel body;
  private boolean requiresSignIn;

  protected Screen() {
    this("");
  }

  protected Screen(final String headingText) {
    final FlowPanel me = new FlowPanel();
    initWidget(me);
    setStyleName("gerrit-Screen");

    me.add(header = new FlowPanel());
    me.add(body = new FlowPanel());

    header.setStyleName("gerrit-ScreenHeader");
    header.add(headerText = new InlineLabel(headingText));
  }

  public void setTitleText(final String text) {
    headerText.setText(text);
  }

  protected void insertTitleWidget(final Widget w) {
    header.insert(w, 0);
  }

  protected final void add(final Widget w) {
    body.add(w);
  }

  /** Set whether or not {@link Gerrit#isSignedIn()} must be true. */
  public void setRequiresSignIn(final boolean b) {
    requiresSignIn = b;
  }

  /** Does {@link Gerrit#isSignedIn()} have to be true to be on this screen? */
  public boolean isRequiresSignIn() {
    return requiresSignIn;
  }

  /** Invoked if this screen is the current screen and the user signs out. */
  public void onSignOut() {
    if (isRequiresSignIn()) {
      History.newItem(Link.ALL_OPEN);
    }
  }

  /** Invoked if this screen is the current screen and the user signs in. */
  public void onSignIn() {
  }
}
