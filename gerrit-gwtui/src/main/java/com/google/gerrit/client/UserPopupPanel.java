// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.ui.InlineHyperlink;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

public class UserPopupPanel extends PopupPanel {
  interface Binder extends UiBinder<Widget, UserPopupPanel> {}

  private static final Binder binder = GWT.create(Binder.class);

  @UiField(provided = true)
  AvatarImage avatar;

  @UiField Label userName;
  @UiField Label userEmail;
  @UiField Element userLinks;
  @UiField AnchorElement switchAccount;
  @UiField AnchorElement logout;
  @UiField InlineHyperlink settings;

  public UserPopupPanel(AccountInfo account, boolean canLogOut, boolean showSettingsLink) {
    super(/* auto hide */ true, /* modal */ false);
    avatar = new AvatarImage(account, 100, false);
    setWidget(binder.createAndBindUi(this));
    setStyleName(Gerrit.RESOURCES.css().userInfoPopup());
    if (account.name() != null) {
      userName.setText(account.name());
    }
    if (account.email() != null) {
      userEmail.setText(account.email());
    }
    if (showSettingsLink) {
      if (Gerrit.info().auth().switchAccountUrl() != null) {
        switchAccount.setHref(Gerrit.info().auth().switchAccountUrl());
      } else if (Gerrit.info().auth().isDev() || Gerrit.info().auth().isOpenId()) {
        switchAccount.setHref(Gerrit.selfRedirect("/login"));
      } else {
        switchAccount.removeFromParent();
        switchAccount = null;
      }
      if (canLogOut) {
        logout.setHref(Gerrit.selfRedirect("/logout"));
      } else {
        logout.removeFromParent();
        logout = null;
      }

    } else {
      settings.removeFromParent();
      settings = null;
      userLinks.removeFromParent();
      userLinks = null;
      logout = null;
    }

    // We must show and then hide this popup so that it is part of the DOM.
    // Otherwise the image does not get any events.  Calling hide() would
    // remove it from the DOM so we use setVisible(false) instead.
    show();
    setVisible(false);
  }
}
