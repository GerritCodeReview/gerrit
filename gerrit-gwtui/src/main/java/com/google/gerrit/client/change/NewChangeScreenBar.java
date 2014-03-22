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

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ChangeScreen;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;

import java.util.Date;

/** Displays a welcome to the new change screen bar. */
class NewChangeScreenBar extends Composite {
  interface Binder extends UiBinder<HTMLPanel, NewChangeScreenBar> {}
  private static final Binder uiBinder = GWT.create(Binder.class);

  static boolean show() {
    if (Gerrit.isSignedIn()) {
      return Gerrit.getUserAccount()
          .getGeneralPreferences()
          .getChangeScreen() == null;
    }
    return Cookies.getCookie(Dispatcher.COOKIE_CS2) == null;
  }

  private final Change.Id id;

  @UiField Element docs;
  @UiField Element anon;
  @UiField Element settings;
  @UiField Anchor keepNew;
  @UiField Anchor keepOld;

  NewChangeScreenBar(Change.Id id) {
    this.id = id;
    initWidget(uiBinder.createAndBindUi(this));
    UIObject.setVisible(docs, Gerrit.getConfig().isDocumentationAvailable());
    UIObject.setVisible(anon, !Gerrit.isSignedIn());
    UIObject.setVisible(settings, Gerrit.isSignedIn());
  }

  @UiHandler("keepOld")
  void onKeepOld(ClickEvent e) {
    save(ChangeScreen.OLD_UI);
    Gerrit.display(PageLinks.toChange(id));
  }

  @UiHandler("keepNew")
  void onKeepNew(ClickEvent e) {
    save(ChangeScreen.CHANGE_SCREEN2);
  }

  private void save(ChangeScreen sel) {
    removeFromParent();
    Dispatcher.changeScreen2 = sel == ChangeScreen.CHANGE_SCREEN2;

    if (Gerrit.isSignedIn()) {
      Gerrit.getUserAccount().getGeneralPreferences().setChangeScreen(sel);

      Prefs in = Prefs.createObject().cast();
      in.change_screen(sel.name());
      AccountApi.self().view("preferences").background().post(in,
        new AsyncCallback<JavaScriptObject>() {
          @Override public void onFailure(Throwable caught) {}
          @Override public void onSuccess(JavaScriptObject result) {}
        });
    } else {
      Cookies.setCookie(
        Dispatcher.COOKIE_CS2,
        Dispatcher.changeScreen2 ? "1" : "0",
        new Date(System.currentTimeMillis() + 7 * 24 * 3600 * 1000));
    }
  }

  private static class Prefs extends JavaScriptObject {
    final native void change_screen(String n) /*-{ this.change_screen=n }-*/;
    protected Prefs() {
    }
  }
}
