// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.client.change;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.impl.HyperlinkImpl;

class Reload extends Image implements ClickHandler,
    MouseOverHandler, MouseOutHandler {
  private static final HyperlinkImpl link = GWT.create(HyperlinkImpl.class);
  private Change.Id changeId;
  private boolean in;

  Reload() {
    setResource(Resources.I.reload_black());
    addClickHandler(this);
    addMouseOverHandler(this);
    addMouseOutHandler(this);
  }

  void set(ChangeInfo info) {
    changeId = info.legacy_id();
  }

  void reload() {
    Gerrit.display(PageLinks.toChange(changeId));
  }

  @Override
  public void onMouseOver(MouseOverEvent event) {
    if (!in) {
      in = true;
      setResource(Resources.I.reload_white());
    }
  }

  @Override
  public void onMouseOut(MouseOutEvent event) {
    if (in) {
      in = false;
      setResource(Resources.I.reload_black());
    }
  }

  @Override
  public void onClick(ClickEvent e) {
    if (link.handleAsClick(e.getNativeEvent().<Event> cast())) {
      e.preventDefault();
      e.stopPropagation();
      reload();
    }
  }
}
