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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.ChangeHeader;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.ui.Hyperlink;

public class ChangeLink extends Hyperlink {
  private ChangeHeader change;

  public ChangeLink(final String text, final ChangeHeader c) {
    super(text, Link.toChange(c));
    change = c;
  }

  @Override
  public void onBrowserEvent(final Event event) {
    if (DOM.eventGetType(event) == Event.ONCLICK) {
      History.newItem(getTargetHistoryToken(), false);
      Gerrit.display(new ChangeScreen(change));
      DOM.eventPreventDefault(event);
    }
  }
}
