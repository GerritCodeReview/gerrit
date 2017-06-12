// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Widget;

public abstract class MenuScreen extends Screen {
  private final LinkMenuBar menu;
  private final FlowPanel body;

  public MenuScreen() {
    menu = new LinkMenuBar();
    menu.setStyleName(Gerrit.RESOURCES.css().menuScreenMenuBar());
    body = new FlowPanel();
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();

    HorizontalPanel hp = new HorizontalPanel();
    hp.add(menu);
    hp.add(body);
    super.add(hp);
  }

  @Override
  public void setToken(String token) {
    LinkMenuItem self = menu.find(token);
    if (self != null) {
      self.addStyleName(Gerrit.RESOURCES.css().activeRow());
    }
    super.setToken(token);
  }

  @Override
  protected FlowPanel getBody() {
    return body;
  }

  @Override
  protected void add(final Widget w) {
    body.add(w);
  }

  protected void link(final String text, final String target) {
    link(text, target, true);
  }

  protected void link(final String text, final String target, final boolean visible) {
    final LinkMenuItem item = new LinkMenuItem(text, target);
    item.setStyleName(Gerrit.RESOURCES.css().menuItem());
    item.setVisible(visible);
    menu.add(item);
  }

  protected void setLinkVisible(final String token, final boolean visible) {
    final LinkMenuItem item = menu.find(token);
    item.setVisible(visible);
  }
}
