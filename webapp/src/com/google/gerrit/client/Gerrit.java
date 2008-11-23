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

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.StackPanel;
import com.google.gwt.user.client.ui.Widget;

public class Gerrit implements EntryPoint {
  /**
   * Name of the Cookie our authentication data is stored in.
   * <p>
   * If this cookie has a value we assume we are signed in.
   * 
   * @see #isSignedIn()
   */
  public static final String AUTH_COOKIE = "GerritAccount";

  public static GerritConstants C;
  private static Link linkManager;

  private static DockPanel body;
  private static Panel topMenu;
  private static StackPanel leftMenu;
  private static FlowPanel codeReviewMenu;
  private static FlowPanel adminMenu;
  private static Screen currentScreen;

  public static void display(final Screen view) {
    if (currentScreen != null) {
      body.remove(currentScreen);
    }

    currentScreen = view;
    body.add(currentScreen, DockPanel.CENTER);
  }

  /** @return true if the user is currently authenticated */
  public static boolean isSignedIn() {
    return Cookies.getCookie(AUTH_COOKIE) != null;
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
    Cookies.removeCookie(AUTH_COOKIE);
    refreshMenus();
  }

  public void onModuleLoad() {
    C = GWT.create(GerritConstants.class);
    linkManager = new Link();
    History.addHistoryListener(linkManager);

    body = new DockPanel();
    body.setWidth("95%");
    body.setHeight(Window.getClientHeight() + "px");
    Window.addWindowResizeListener(new WindowResizeListener() {
      public void onWindowResized(final int width, final int height) {
        body.setHeight(height + "px");
      }
    });
    RootPanel.get("gerrit_body").add(body);

    topMenu = new FlowPanel();
    topMenu.addStyleName("gerrit-TopMenu");

    codeReviewMenu = createMenuList();
    leftMenu = new StackPanel();
    leftMenu.addStyleName("gerrit-LeftMenu");
    leftMenu.add(codeReviewMenu, C.leftMenuCodeReviews());

    body.add(topMenu, DockPanel.NORTH);
    body.add(leftMenu, DockPanel.WEST);
    body.setCellHeight(topMenu, "20px");
    body.setCellWidth(leftMenu, "150px");
    refreshMenus();

    if ("".equals(History.getToken())) {
      History.newItem(Link.MINE);
    } else {
      History.fireCurrentHistoryState();
    }
  }

  /** Hook from {@link SignInDialog} to let us know to refresh the UI. */
  static void postSignIn() {
    refreshMenus();
  }

  private static void refreshMenus() {
    refreshTopMenu();
    refreshCodeReviewMenu();

    if (isSignedIn()) {
      if (adminMenu == null) {
        adminMenu = createAdminMenu();
        leftMenu.add(adminMenu, C.leftMenuAdmin());
      }
    } else {
      if (adminMenu != null) {
        leftMenu.remove(adminMenu);
        adminMenu = null;
      }
    }
  }

  private static void refreshTopMenu() {
    final Panel m = topMenu;
    m.clear();

    if (isSignedIn()) {
      m.add(new Hyperlink(C.menuSettings(), Link.SETTINGS));
      m.add(new HTML("&nbsp;|&nbsp;"));
      {
        final Hyperlink signout = new Hyperlink();
        signout.setText(C.menuSignOut());
        signout.addStyleName("gerrit-Hyperlink");
        signout.addClickListener(new ClickListener() {
          public void onClick(Widget sender) {
            doSignOut();
          }
        });
        m.add(signout);
      }
    } else {
      {
        final Hyperlink signin = new Hyperlink();
        signin.setText(C.menuSignIn());
        signin.addStyleName("gerrit-Hyperlink");
        signin.addClickListener(new ClickListener() {
          public void onClick(Widget sender) {
            doSignIn(null);
          }
        });
        m.add(signin);
      }
    }
  }

  private static FlowPanel createMenuList() {
    final FlowPanel m = new FlowPanel();
    m.setStyleName("gerrit-MenuList");
    return m;
  }

  private static void refreshCodeReviewMenu() {
    final boolean signedIn = isSignedIn();
    final FlowPanel m = codeReviewMenu;
    m.clear();

    if (signedIn) {
      m.add(new Hyperlink(C.menuMyChanges(), Link.MINE));
      m.add(new Hyperlink(C.menuMyUnclaimedChanges(), Link.MINE_UNCLAIMED));
    }

    m.add(new Hyperlink(C.menuAllRecentChanges(), Link.ALL));
    m.add(new Hyperlink(C.menuAllUnclaimedChanges(), Link.ALL_UNCLAIMED));

    if (signedIn) {
      m.add(new Hyperlink(C.menuMyStarredChanges(), Link.MINE_STARRED));
    }
  }

  private static FlowPanel createAdminMenu() {
    final FlowPanel m = createMenuList();
    m.add(new Hyperlink(C.menuPeople(), Link.ADMIN_PEOPLE));
    m.add(new Hyperlink(C.menuGroups(), Link.ADMIN_GROUPS));
    m.add(new Hyperlink(C.menuProjects(), Link.ADMIN_PROJECTS));
    return m;
  }
}
