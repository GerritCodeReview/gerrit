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

import com.google.gerrit.client.info.ChangeInfo.MessageInfo;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.List;

/** Displays the "New Message From ..." panel in bottom right on updates. */
abstract class UpdateAvailableBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, UpdateAvailableBar> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private Timestamp updated;

  @UiField Element author;
  @UiField Anchor show;
  @UiField Anchor ignore;

  UpdateAvailableBar() {
    initWidget(uiBinder.createAndBindUi(this));
  }

  void set(List<MessageInfo> newMessages, Timestamp newTime) {
    HashSet<Integer> seen = new HashSet<>();
    StringBuilder r = new StringBuilder();
    for (MessageInfo m : newMessages) {
      int a = m.author() != null ? m.author()._accountId() : 0;
      if (seen.add(a)) {
        if (r.length() > 0) {
          r.append(", ");
        }
        r.append(Message.authorName(m));
      }
    }
    author.setInnerText(r.toString());
    updated = newTime;
  }

  @UiHandler("show")
  void onShow(@SuppressWarnings("unused") ClickEvent e) {
    onShow();
  }

  @UiHandler("ignore")
  void onIgnore(@SuppressWarnings("unused") ClickEvent e) {
    onIgnore(updated);
    removeFromParent();
  }

  abstract void onShow();

  abstract void onIgnore(Timestamp newTime);
}
