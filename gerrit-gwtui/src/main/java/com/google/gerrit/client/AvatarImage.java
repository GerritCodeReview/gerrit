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

import com.google.gerrit.client.changes.Util;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.AccountInfo.AvatarInfo;
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

public class AvatarImage extends Image implements LoadHandler {
  public AvatarImage() {
    setVisible(false);
    addLoadHandler(this);
  }

  /** A default sized avatar image. */
  public AvatarImage(AccountInfo account) {
    this(account, AccountInfo.AvatarInfo.DEFAULT_SIZE, true);
  }

  /**
   * An avatar image for the given account using the requested size.
   *
   * @param account The account in which we are interested
   * @param size A requested size. Note that the size can be ignored depending on the avatar
   *     provider. A size <= 0 indicates to let the provider decide a default size.
   * @param addPopup show avatar popup with user info on hovering over the avatar image
   */
  public AvatarImage(AccountInfo account, int size, boolean addPopup) {
    addLoadHandler(this);
    setAccount(account, size, addPopup);
  }

  public void setAccount(AccountInfo account, int size, boolean addPopup) {
    if (account == null) {
      setVisible(false);
    } else if (isGerritServer(account)) {
      setVisible(true);
      setResource(Gerrit.RESOURCES.gerritAvatar26());
    } else if (account.hasAvatarInfo()) {
      setVisible(false);
      AvatarInfo info = account.avatar(size);
      if (info != null) {
        setWidth(info.width() > 0 ? info.width() + "px" : "");
        setHeight(info.height() > 0 ? info.height() + "px" : "");
        setUrl(info.url());
        popup(account, addPopup);
      } else if (account.email() != null) {
        loadAvatar(account, size, addPopup);
      }
    } else if (account.email() != null) {
      loadAvatar(account, size, addPopup);
    } else {
      setVisible(false);
    }
  }

  private void loadAvatar(AccountInfo account, int size, boolean addPopup) {
    if (!Gerrit.info().plugin().hasAvatars()) {
      setVisible(false);
      return;
    }

    // TODO Kill /accounts/*/avatar URL.
    String u = account.email();
    if (Gerrit.isSignedIn() && u.equals(Gerrit.getUserAccount().email())) {
      u = "self";
    }
    RestApi api = new RestApi("/accounts/").id(u).view("avatar");
    if (size > 0) {
      api.addParameter("s", size);
      setSize("", size + "px");
    }
    setVisible(false);
    setUrl(api.url());
    popup(account, addPopup);
  }

  private void popup(AccountInfo account, boolean addPopup) {
    if (addPopup) {
      PopupHandler popupHandler = new PopupHandler(account, this);
      addMouseOverHandler(popupHandler);
      addMouseOutHandler(popupHandler);
    }
  }

  @Override
  public void onLoad(LoadEvent event) {
    setVisible(true);
  }

  private static boolean isGerritServer(AccountInfo account) {
    return account._accountId() == 0 && Util.C.messageNoAuthor().equals(account.name());
  }

  private static class PopupHandler implements MouseOverHandler, MouseOutHandler {
    private final AccountInfo account;
    private final UIObject target;

    private UserPopupPanel popup;
    private Timer showTimer;
    private Timer hideTimer;

    PopupHandler(AccountInfo account, UIObject target) {
      this.account = account;
      this.target = target;
    }

    private UserPopupPanel createPopupPanel(AccountInfo account) {
      UserPopupPanel popup = new UserPopupPanel(account, false, false);
      popup.addDomHandler(
          new MouseOverHandler() {
            @Override
            public void onMouseOver(MouseOverEvent event) {
              scheduleShow();
            }
          },
          MouseOverEvent.getType());
      popup.addDomHandler(
          new MouseOutHandler() {
            @Override
            public void onMouseOut(MouseOutEvent event) {
              scheduleHide();
            }
          },
          MouseOutEvent.getType());
      return popup;
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
      if ((popup != null && popup.isShowing() && popup.isVisible()) || showTimer != null) {
        return;
      }
      showTimer =
          new Timer() {
            @Override
            public void run() {
              if (popup == null) {
                popup = createPopupPanel(account);
              }
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
      if (popup == null || !popup.isShowing() || !popup.isVisible() || hideTimer != null) {
        return;
      }
      hideTimer =
          new Timer() {
            @Override
            public void run() {
              popup.hide();
            }
          };
      hideTimer.schedule(50);
    }
  }
}
