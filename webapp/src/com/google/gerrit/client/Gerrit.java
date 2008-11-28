// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.LinkMenuItem;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtjsonrpc.client.JsonUtil;

import java.util.ArrayList;

public class Gerrit implements EntryPoint {
  /**
   * Name of the Cookie our authentication data is stored in.
   * <p>
   * If this cookie has a value we assume we are signed in.
   * 
   * @see #isSignedIn()
   */
  public static final String ACCOUNT_COOKIE = "GerritAccount";
  public static final String OPENIDUSER_COOKIE = "GerritOpenIdUser";

  public static GerritConstants C;
  public static GerritIcons ICONS;
  private static Link linkManager;
  private static ArrayList<SignedInListener> signedInListeners =
      new ArrayList<SignedInListener>();

  private static LinkMenuBar menuBar;
  private static RootPanel body;
  private static Screen currentScreen;

  public static void display(final Screen view) {
    if (view.isRequiresSignIn() && !isSignedIn()) {
      doSignIn(new AsyncCallback<Object>() {
        public void onSuccess(final Object result) {
          display(view);
        }

        public void onFailure(final Throwable caught) {
        }
      });
      return;
    }

    if (currentScreen != null) {
      body.remove(currentScreen);
    }

    currentScreen = view;
    body.add(currentScreen);
  }

  /** @return true if the user is currently authenticated */
  public static boolean isSignedIn() {
    return Cookies.getCookie(ACCOUNT_COOKIE) != null;
  }

  /**
   * Sign the user into the application.
   * 
   * @param callback optional; if sign in is successful the onSuccess method
   *        will be called.
   */
  public static void doSignIn(final AsyncCallback<?> callback) {
    new SignInDialog(callback).center();
  }

  /** Sign the user out of the application (and discard the cookies). */
  public static void doSignOut() {
    Cookies.removeCookie(ACCOUNT_COOKIE);
    Cookies.removeCookie(OPENIDUSER_COOKIE);

    for (final SignedInListener l : signedInListeners) {
      l.onSignOut();
    }
    refreshMenuBar();

    if (currentScreen != null && currentScreen.isRequiresSignIn()) {
      History.newItem(Link.ALL);
    }
  }

  /** Add a listener to monitor sign-in status. */
  public static void addSignedInListener(final SignedInListener l) {
    if (!signedInListeners.contains(l)) {
      signedInListeners.add(l);
    }
  }

  /** Remove a previously added sign in listener. */
  public static void removeSignedInListener(final SignedInListener l) {
    signedInListeners.remove(l);
  }

  public void onModuleLoad() {
    C = GWT.create(GerritConstants.class);
    ICONS = GWT.create(GerritIcons.class);

    linkManager = new Link();
    History.addHistoryListener(linkManager);

    final RootPanel topMenu = RootPanel.get("gerrit_topmenu");
    JsonUtil.addRpcStatusListener(new RpcStatus(topMenu));
    menuBar = new LinkMenuBar();
    topMenu.add(menuBar);

    body = RootPanel.get("gerrit_body");
    body.setHeight(Window.getClientHeight() + "px");
    Window.addWindowResizeListener(new WindowResizeListener() {
      public void onWindowResized(final int width, final int height) {
        body.setHeight(height + "px");
      }
    });

    refreshMenuBar();

    if ("".equals(History.getToken())) {
      if (isSignedIn()) {
        History.newItem(Link.MINE);
      } else {
        History.newItem(Link.ALL);
      }
    } else {
      History.fireCurrentHistoryState();
    }
  }

  /** Hook from {@link SignInDialog} to let us know to refresh the UI. */
  static void postSignIn() {
    refreshMenuBar();
    for (final SignedInListener l : signedInListeners) {
      l.onSignIn();
    }
  }

  private static void refreshMenuBar() {
    menuBar.clearItems();

    final boolean signedIn = isSignedIn();
    MenuBar m;

    m = new MenuBar(true);
    addLink(m, C.menuAllRecentChanges(), Link.ALL);
    addLink(m, C.menuAllUnclaimedChanges(), Link.ALL_UNCLAIMED);
    menuBar.addItem(C.menuAll(), m);

    if (signedIn) {
      m = new MenuBar(true);
      addLink(m, C.menuMyChanges(), Link.MINE);
      addLink(m, C.menuMyUnclaimedChanges(), Link.MINE_UNCLAIMED);
      addLink(m, C.menuMyStarredChanges(), Link.MINE_STARRED);
      menuBar.addItem(C.menuMine(), m);
    }

    if (signedIn) {
      m = new MenuBar(true);
      addLink(m, C.menuPeople(), Link.ADMIN_PEOPLE);
      addLink(m, C.menuGroups(), Link.ADMIN_GROUPS);
      addLink(m, C.menuProjects(), Link.ADMIN_PROJECTS);
      menuBar.addItem(C.menuAdmin(), m);
    }

    menuBar.lastInGroup();
    menuBar.addGlue();

    if (signedIn) {
      menuBar.addItem(new LinkMenuItem(C.menuSettings(), Link.SETTINGS));
      menuBar.addItem(C.menuSignOut(), new Command() {
        public void execute() {
          doSignOut();
        }
      });
    } else {
      menuBar.addItem(C.menuSignIn(), new Command() {
        public void execute() {
          doSignIn(null);
        }
      });
    }
    menuBar.lastInGroup();
  }

  private static void addLink(final MenuBar m, final String text,
      final String historyToken) {
    m.addItem(new LinkMenuItem(text, historyToken));
  }
}
