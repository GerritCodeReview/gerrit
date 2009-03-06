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

import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.MenuItem;

/**
 * A GWT {@link MenuItem} that uses a normal HTML link widget for its UI.
 * <p>
 * Using this widget instead of MenuItem permits the menu item to have the
 * standard right-click "Open in new window" and "Open in new tab" feature found
 * in popular browsers.
 */
public class LinkMenuItem extends MenuItem {
  /**
   * Creates a hyperlink with its text and target history token specified.
   * 
   * @param text the hyperlink's text
   * @param targetHistoryToken the history token to which it will link
   */
  public LinkMenuItem(final String text, final String targetHistoryToken) {
    super("", new Command() {
      public void execute() {
        History.newItem(targetHistoryToken);
      }
    });
    final Element a = DOM.createAnchor();
    DOM.setElementProperty(a, "href", "#" + targetHistoryToken);
    DOM.setInnerText(a, text);
    DOM.appendChild(getElement(), a);
  }
}
