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

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.PluginSafePopupPanel;

public class CurrentUserPopupPanel extends PluginSafePopupPanel {
  interface Binder extends UiBinder<Widget, CurrentUserPopupPanel> {
  }

  private static final Binder binder = GWT.create(Binder.class);

  @UiField(provided = true)
  AvatarImage avatar;
  @UiField
  Label userName;
  @UiField
  Label userEmail;
  @UiField
  Anchor logout;
  @UiField
  Anchor settings;

  public CurrentUserPopupPanel(Account account, boolean canLogOut) {
    super(/* auto hide */true, /* modal */false);
    avatar = new AvatarImage(account.getPreferredEmail(), 100);
    setWidget(binder.createAndBindUi(this));
    // We must show and then hide this popup so that it is part of the DOM.
    // Otherwise the image does not get any events.  Calling hide() would
    // remove it from the DOM so we use setVisible(false) instead.
    show();
    setVisible(false);
    setStyleName(Gerrit.RESOURCES.css().userInfoPopup());
    if (account.getFullName() != null) {
      userName.setText(account.getFullName());
    }
    if (account.getPreferredEmail() != null) {
      userEmail.setText(account.getPreferredEmail());
    }
    if (canLogOut) {
      logout.setHref(Gerrit.selfRedirect("/logout"));
    } else {
      logout.setVisible(false);
    }
    settings.setHref(Gerrit.selfRedirect(PageLinks.SETTINGS));
  }
}
