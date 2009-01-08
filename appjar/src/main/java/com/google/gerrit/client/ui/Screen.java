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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.FlowPanel;

public class Screen extends FlowPanel {
  private boolean requiresSignIn;
  private Element headerElem;

  protected Screen() {
    this("");
  }

  protected Screen(final String headingText) {
    setStyleName("gerrit-Screen");

    headerElem = DOM.createElement("h1");
    DOM.appendChild(getElement(), headerElem);

    setTitleText(headingText);
  }

  public void setTitleText(final String text) {
    DOM.setInnerText(headerElem, text);
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
      History.newItem(Link.ALL);
    }
  }

  /** Invoked if this screen is the current screen and the user signs in. */
  public void onSignIn() {
  }

  /** Get the token to cache this screen's widget; null if it shouldn't cache. */
  public Object getScreenCacheToken() {
    return null;
  }

  /**
   * Reconfigure this screen after being recycled.
   * <p>
   * This method is invoked on a cached screen instance just before it is
   * recycled into the UI. The returned screen instance is what will actually be
   * shown to the user.
   * 
   * @param newScreen the screen object created by the Link class (or some other
   *        form of screen constructor) and that was just passed into
   *        {@link Gerrit#display(Screen)}. Its {@link #getScreenCacheToken()}
   *        is equal to <code>this.getScreenCacheToken()</code> but it may have
   *        other parameter information worth copying.
   * @return typically <code>this</code> to reuse the cached screen;
   *         <code>newScreen</code> to discard the cached screen instance and
   *         use the new one.
   */
  public Screen recycleThis(final Screen newScreen) {
    return this;
  }
}
