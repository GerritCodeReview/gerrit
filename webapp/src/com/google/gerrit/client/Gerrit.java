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
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.WindowResizeListener;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Hyperlink;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.StackPanel;

public class Gerrit implements EntryPoint {
  public static GerritConstants C;
  private static Link linkManager;

  static DockPanel body;
  static StackPanel leftMenu;
  private static Screen currentScreen;

  public static void display(final Screen view) {
    if (currentScreen != null) {
      body.remove(currentScreen);
    }

    currentScreen = view;
    body.add(currentScreen, DockPanel.CENTER);
  }

  public void onModuleLoad() {
    C = GWT.create(GerritConstants.class);
    linkManager = new Link();
    History.addHistoryListener(linkManager);

    body = new DockPanel();
    body.setWidth("100%");
    body.setHeight(Window.getClientHeight() + "px");
    Window.addWindowResizeListener(new WindowResizeListener() {
      public void onWindowResized(final int width, final int height) {
        body.setHeight(height + "px");
      }
    });
    RootPanel.get("gerrit_body").add(body);

    leftMenu = new StackPanel();
    leftMenu.addStyleName("gerrit-LeftMenu");
    leftMenu.add(createCodeReviewMenu(), C.leftMenuCodeReviews());
    leftMenu.add(createAdminMenu(), C.leftMenuAdmin());
    body.add(leftMenu, DockPanel.WEST);
    body.setCellWidth(leftMenu, "150px");

    if ("".equals(History.getToken())) {
      History.newItem(Link.MINE);
    } else {
      History.fireCurrentHistoryState();
    }
  }

  private FlowPanel createCodeReviewMenu() {
    final FlowPanel menu = new FlowPanel();
    menu.setStyleName("gerrit-MenuList");

    menu.add(new Hyperlink(C.menuMyChanges(), Link.MINE));
    menu.add(new Hyperlink(C.menuMyUnclaimedChanges(), Link.MINE_UNCLAIMED));
    menu.add(new Hyperlink(C.menuAllRecentChanges(), Link.ALL));
    menu.add(new Hyperlink(C.menuAllUnclaimedChanges(), Link.ALL_UNCLAIMED));
    menu.add(new Hyperlink(C.menuMyStarredChanges(), Link.MINE_STARRED));

    return menu;
  }

  private FlowPanel createAdminMenu() {
    final FlowPanel menu = new FlowPanel();
    menu.setStyleName("gerrit-MenuList");

    menu.add(new Hyperlink(C.menuPeople(), Link.ADMIN_PEOPLE));
    menu.add(new Hyperlink(C.menuGroups(), Link.ADMIN_GROUPS));
    menu.add(new Hyperlink(C.menuProjects(), Link.ADMIN_PROJECTS));

    return menu;
  }
}
