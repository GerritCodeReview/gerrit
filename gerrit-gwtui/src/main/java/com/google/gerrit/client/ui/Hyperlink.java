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
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;

/** Standard GWT hyperlink with late updating of the token. */
public class Hyperlink extends com.google.gwt.user.client.ui.Hyperlink {
  public static final HyperlinkImpl impl = GWT.create(HyperlinkImpl.class);

  /** Initialize a default hyperlink with no target and no text. */
  public Hyperlink() {}

  /**
   * Creates a hyperlink with its text and target history token specified.
   *
   * @param text the hyperlink's text
   * @param token the history token to which it will link, which may not be null (use {@link Anchor}
   *     instead if you don't need history processing)
   */
  public Hyperlink(final String text, final String token) {
    super(text, token);
  }

  /**
   * Creates a hyperlink with its text and target history token specified.
   *
   * @param text the hyperlink's text
   * @param asHTML {@code true} to treat the specified text as html
   * @param token the history token to which it will link
   * @see #setTargetHistoryToken
   */
  public Hyperlink(String text, boolean asHTML, String token) {
    super(text, asHTML, token);
  }

  @Override
  public void onBrowserEvent(final Event event) {
    if (DOM.eventGetType(event) == Event.ONCLICK && impl.handleAsClick(event)) {
      event.preventDefault();
      go();
    } else {
      super.onBrowserEvent(event);
    }
  }

  /** Create the screen and start rendering, updating the browser history. */
  public void go() {
    Gerrit.display(getTargetHistoryToken());
  }
}
