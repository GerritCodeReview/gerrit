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

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Accessibility;
import com.google.gwt.user.client.ui.InlineHyperlink;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;

public class LinkMenuItem extends InlineHyperlink {
  static final HyperlinkImpl impl = GWT.create(HyperlinkImpl.class);

  public LinkMenuItem(final String text, final String targetHistoryToken) {
    super(text, targetHistoryToken);
    setStyleName("gerrit-MenuItem");
    Accessibility.setRole(getElement(), Accessibility.ROLE_MENUITEM);
  }

  @Override
  public void onBrowserEvent(Event event) {
    super.onBrowserEvent(event);
    if (DOM.eventGetType(event) == Event.ONCLICK && impl.handleAsClick(event)) {
      AnchorElement.as(getElement()).blur();
    }
  }
}
