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
  private static Link linkManager;

  private static LinkMenuBar menuBar;
  private static RootPanel body;
  private static Screen currentScreen;

  public static void display(final Screen view) {
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
    refreshMenuBar();
  }

  public void onModuleLoad() {
    C = GWT.create(GerritConstants.class);
    linkManager = new Link();
    History.addHistoryListener(linkManager);

    menuBar = new LinkMenuBar();
    RootPanel.get("gerrit_topmenu").add(menuBar);

    body = RootPanel.get("gerrit_body");
    body.setHeight(Window.getClientHeight() + "px");
    Window.addWindowResizeListener(new WindowResizeListener() {
      public void onWindowResized(final int width, final int height) {
        body.setHeight(height + "px");
      }
    });

    refreshMenuBar();

    if ("".equals(History.getToken())) {
      History.newItem(Link.MINE);
    } else {
      History.fireCurrentHistoryState();
    }
  }

  /** Hook from {@link SignInDialog} to let us know to refresh the UI. */
  static void postSignIn() {
    refreshMenuBar();
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
