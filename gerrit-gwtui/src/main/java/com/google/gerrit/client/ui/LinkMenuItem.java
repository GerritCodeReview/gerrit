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
import com.google.gwt.dom.client.AnchorElement;

public class LinkMenuItem extends InlineHyperlink implements ScreenLoadHandler {
  private LinkMenuBar bar;

  public LinkMenuItem(String text, String targetHistoryToken) {
    super(text, targetHistoryToken);
    setStyleName(Gerrit.RESOURCES.css().menuItem());
    Roles.getMenuitemRole().set(getElement());
    Gerrit.EVENT_BUS.addHandler(ScreenLoadEvent.TYPE, this);
  }

  @Override
  public void go() {
    super.go();
    AnchorElement.as(getElement()).blur();
  }

  public void setMenuBar(LinkMenuBar bar) {
    this.bar = bar;
  }

  @Override
  public void onScreenLoad(ScreenLoadEvent event) {
    if (match(event.getScreen().getToken())) {
      Gerrit.selectMenu(bar);
      addStyleName(Gerrit.RESOURCES.css().activeRow());
    } else {
      removeStyleName(Gerrit.RESOURCES.css().activeRow());
    }
  }

  protected boolean match(String token) {
    return token.equals(getTargetHistoryToken());
  }
}
