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
import com.google.gwt.aria.client.Roles;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class LinkMenuBar extends Composite implements ScreenLoadHandler {
  private final FlowPanel body;

  public LinkMenuBar() {
    body = new FlowPanel();
    initWidget(body);
    setStyleName(Gerrit.RESOURCES.css().linkMenuBar());
    Roles.getMenubarRole().set(getElement());
    Gerrit.EVENT_BUS.addHandler(ScreenLoadEvent.TYPE, this);
  }

  public void addItem(String text, Command imp) {
    add(new CommandMenuItem(text, imp));
  }

  public void addItem(CommandMenuItem i) {
    add(i);
  }

  public void addItem(LinkMenuItem i) {
    i.setMenuBar(this);
    add(i);
  }

  public void insertItem(LinkMenuItem i, int beforeIndex) {
    i.setMenuBar(this);
    insert(i, beforeIndex);
  }

  public void clear() {
    body.clear();
  }

  public LinkMenuItem find(String targetToken) {
    for (Widget w : body) {
      if (w instanceof LinkMenuItem) {
        LinkMenuItem m = (LinkMenuItem) w;
        if (targetToken.equals(m.getTargetHistoryToken())) {
          return m;
        }
      }
    }
    return null;
  }

  public void add(Widget i) {
    if (body.getWidgetCount() > 0) {
      final Widget p = body.getWidget(body.getWidgetCount() - 1);
      p.addStyleName(Gerrit.RESOURCES.css().linkMenuItemNotLast());
    }
    body.add(i);
  }

  public void insert(Widget i, int beforeIndex) {
    if (body.getWidgetCount() == 0 || body.getWidgetCount() <= beforeIndex) {
      add(i);
      return;
    }
    body.insert(i, beforeIndex);
  }

  public int getWidgetIndex(Widget i) {
    return body.getWidgetIndex(i);
  }

  @Override
  public void onScreenLoad(ScreenLoadEvent event) {}
}
