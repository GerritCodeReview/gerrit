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

import static com.google.gerrit.client.ui.Hyperlink.impl;

import com.google.gerrit.client.Gerrit;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;

/** Standard GWT hyperlink with late updating of the token. */
public class InlineHyperlink extends com.google.gwt.user.client.ui.InlineHyperlink {
  /**
   * Creates a link with its text and target history token specified.
   *
   * @param text the hyperlink's text
   * @param token the history token to which it will link
   */
  public InlineHyperlink(String text, String token) {
    super(text, token);
  }

  /** Creates an empty link. */
  public InlineHyperlink() {}

  @Override
  public void onBrowserEvent(Event event) {
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
