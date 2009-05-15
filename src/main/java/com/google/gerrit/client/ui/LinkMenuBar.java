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
import com.google.gwt.user.client.ui.Accessibility;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class LinkMenuBar extends Composite {
  private final FlowPanel body;

  public LinkMenuBar() {
    body = new FlowPanel();
    initWidget(body);
    setStyleName("gerrit-LinkMenuBar");
    Accessibility.setRole(getElement(), Accessibility.ROLE_MENUBAR);
  }

  public void addItem(final String text, final Command imp) {
    add(new CommandMenuItem(text, imp));
  }

  public void addItem(final CommandMenuItem i) {
    add(i);
  }

  public void addItem(final LinkMenuItem i) {
    add(i);
  }

  public void clear() {
    body.clear();
  }

  public void add(final Widget i) {
    if (body.getWidgetCount() > 0) {
      final Widget p = body.getWidget(body.getWidgetCount() - 1);
      p.addStyleName("gerrit-LinkMenuItem-NotLast");
    }
    i.addStyleName("gerrit-LinkMenuItem");
    body.add(i);
  }
}
