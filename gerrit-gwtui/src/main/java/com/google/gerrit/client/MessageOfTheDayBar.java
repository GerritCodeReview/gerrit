// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client;

import com.google.gerrit.common.data.HostPageData;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtexpui.safehtml.client.SafeHtml;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;
import java.util.ArrayList;
import java.util.List;

/** Displays pending messages from the server. */
class MessageOfTheDayBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, MessageOfTheDayBar> {}

  private static final Binder uiBinder = GWT.create(Binder.class);

  private final List<HostPageData.Message> motd;
  @UiField HTML message;
  @UiField Anchor dismiss;

  MessageOfTheDayBar(List<HostPageData.Message> motd) {
    this.motd = filter(motd);
    initWidget(uiBinder.createAndBindUi(this));

    SafeHtmlBuilder b = new SafeHtmlBuilder();
    if (motd.size() == 1) {
      b.append(SafeHtml.asis(motd.get(0).html));
    } else {
      for (HostPageData.Message m : motd) {
        b.openDiv();
        b.append(SafeHtml.asis(m.html));
        b.openElement("hr");
        b.closeSelf();
        b.closeDiv();
      }
    }
    message.setHTML(b);
  }

  void show() {
    if (!motd.isEmpty()) {
      RootPanel.get().add(this);
    }
  }

  @UiHandler("dismiss")
  void onDismiss(@SuppressWarnings("unused") ClickEvent e) {
    removeFromParent();

    for (HostPageData.Message m : motd) {
      Cookies.setCookie(cookieName(m), "1", m.redisplay);
    }
  }

  private static List<HostPageData.Message> filter(List<HostPageData.Message> in) {
    List<HostPageData.Message> show = new ArrayList<>();
    for (HostPageData.Message m : in) {
      if (Cookies.getCookie(cookieName(m)) == null) {
        show.add(m);
      }
    }
    return show;
  }

  private static String cookieName(HostPageData.Message m) {
    return "msg-" + m.id;
  }
}
