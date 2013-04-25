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

package com.google.gerrit.client;

import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.UIObject;

public class AvatarImage extends Image {

  public AvatarImage() {
  }

  /** A default sized avatar image. */
  public AvatarImage(AccountInfo account) {
    this(account, 0);
  }

  /**
   * An avatar image for the given account using the requested size.
   *
   * @param account The account in which we are interested
   * @param size A requested size. Note that the size can be ignored depending
   *        on the avatar provider. A size <= 0 indicates to let the provider
   *        decide a default size.
   */
  public AvatarImage(AccountInfo account, int size) {
    this(account, size, true);
  }

  /**
   * An avatar image for the given account using the requested size.
   *
   * @param account The account in which we are interested
   * @param size A requested size. Note that the size can be ignored depending
   *        on the avatar provider. A size <= 0 indicates to let the provider
   *        decide a default size.
   * @param addPopup show avatar popup with user info on hovering over the
   *        avatar image
   */
  public AvatarImage(AccountInfo account, int size, boolean addPopup) {
    setAccount(account, size, addPopup);
  }

  public void setAccount(AccountInfo account, int size, boolean addPopup) {
    setUrl(isGerritServer(account) ? getGerritServerAvatarUrl() :
      url(account.email(), size));
    setVisible(false);

    if (size > 0) {
      // If the provider does not resize the image, force it in the browser.
      setSize(size + "px", size + "px");
    }

    addLoadHandler(new LoadHandler() {
      @Override
      public void onLoad(LoadEvent event) {
        setVisible(true);
      }
    });

    if (addPopup) {
      UserPopupPanel userPopup = new UserPopupPanel(account, false, false);
      PopupHandler popupHandler = new PopupHandler(userPopup, this);
      addMouseOverHandler(popupHandler);
      addMouseOutHandler(popupHandler);
    }
  }

  private static boolean isGerritServer(AccountInfo account) {
    return account._account_id() == 0
        && Util.C.messageNoAuthor().equals(account.name());
  }

  private static String getGerritServerAvatarUrl() {
    return Gerrit.RESOURCES.gerritAvatar().getSafeUri().asString();
  }

  private static String url(String email, int size) {
    if (email == null) {
      return "";
    }
    String u;
    if (Gerrit.isSignedIn() && email.equals(Gerrit.getUserAccount().getPreferredEmail())) {
      u = "self";
    } else {
      u = email;
    }
    RestApi api = new RestApi("/accounts/").id(u).view("avatar");
    if (size > 0) {
      api.addParameter("s", size);
    }
    return api.url();
  }

  private class PopupHandler implements MouseOverHandler, MouseOutHandler {
    private final UserPopupPanel popup;
    private final UIObject target;

    private Timer showTimer;
    private Timer hideTimer;

    public PopupHandler(UserPopupPanel popup, UIObject target) {
      this.popup = popup;
      this.target = target;

      popup.addDomHandler(new MouseOverHandler() {
        @Override
        public void onMouseOver(MouseOverEvent event) {
          scheduleShow();
        }
      }, MouseOverEvent.getType());
      popup.addDomHandler(new MouseOutHandler() {
        @Override
        public void onMouseOut(MouseOutEvent event) {
          scheduleHide();
        }
      }, MouseOutEvent.getType());
    }

    @Override
    public void onMouseOver(MouseOverEvent event) {
      scheduleShow();
    }

    @Override
    public void onMouseOut(MouseOutEvent event) {
      scheduleHide();
    }

    private void scheduleShow() {
      if (hideTimer != null) {
        hideTimer.cancel();
        hideTimer = null;
      }
      if ((popup.isShowing() && popup.isVisible()) || showTimer != null) {
        return;
      }
      showTimer = new Timer() {
        @Override
        public void run() {
          if (!popup.isShowing() || !popup.isVisible()) {
            popup.showRelativeTo(target);
          }

        }
      };
      showTimer.schedule(600);
    }

    private void scheduleHide() {
      if (showTimer != null) {
        showTimer.cancel();
        showTimer = null;
      }
      if (!popup.isShowing() || !popup.isVisible() || hideTimer != null) {
        return;
      }
      hideTimer = new Timer() {
        @Override
        public void run() {
          popup.hide();
        }
      };
      hideTimer.schedule(50);
    }
  }


}
