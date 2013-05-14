// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.projects.ThemeInfo;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwtexpui.user.client.View;

/**
  *  A Screen layout with a header and a body.
  *
  * The header is mainly a text title, but it can be decorated
  * in the West, the East, and the FarEast by any Widget.  The
  * West and East decorations will surround the text on the
  * left and right respectively, and the FarEast will be right
  * justified to the right edge of the screen.  The East
  * decoration will expand to take up any extra space.
  */
public abstract class Screen extends View {
  private Grid header;
  private InlineLabel headerText;
  private FlowPanel body;
  private String token;
  private boolean requiresSignIn;
  private String windowTitle;
  private Widget titleWidget;

  private ThemeInfo theme;
  private boolean setTheme;

  protected Screen() {
    initWidget(new FlowPanel());
    setStyleName(Gerrit.RESOURCES.css().screen());
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    if (header == null) {
      onInitUI();
    }
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    if (setTheme) {
      Gerrit.THEMER.clear();
    }
  }

  public void registerKeys() {
  }

  private static enum Cols {
    West, Title, East, FarEast;
  }

  protected void onInitUI() {
    final FlowPanel me = (FlowPanel) getWidget();
    me.add(header = new Grid(1, Cols.values().length));
    me.add(body = new FlowPanel());

    headerText = new InlineLabel();
    if (titleWidget == null) {
      titleWidget = headerText;
    }
    FlowPanel title = new FlowPanel();
    title.add(titleWidget);
    title.setStyleName(Gerrit.RESOURCES.css().screenHeader());
    header.setWidget(0, Cols.Title.ordinal(), title);

    header.setStyleName(Gerrit.RESOURCES.css().screenHeader());
    header.getCellFormatter().setHorizontalAlignment(0, Cols.FarEast.ordinal(),
      HasHorizontalAlignment.ALIGN_RIGHT);
    // force FarEast all the way to the right
    header.getCellFormatter().setWidth(0, Cols.FarEast.ordinal(), "100%");
  }

  protected void setWindowTitle(final String text) {
    windowTitle = text;
    Gerrit.setWindowTitle(this, text);
  }

  protected void setPageTitle(final String text) {
    final String old = headerText.getText();
    if (text.isEmpty()) {
      header.setVisible(false);
    } else {
      headerText.setText(text);
      header.setVisible(true);
    }
    if (windowTitle == null || windowTitle == old) {
      setWindowTitle(text);
    }
  }

  protected void setHeaderVisible(boolean value) {
    header.setVisible(value);
  }

  public void setTitle(final Widget w) {
    titleWidget = w;
  }

  protected void setTitleEast(final Widget w) {
    header.setWidget(0, Cols.East.ordinal(), w);
  }

  protected void setTitleFarEast(final Widget w) {
    header.setWidget(0, Cols.FarEast.ordinal(), w);
  }

  protected void setTitleWest(final Widget w) {
    header.setWidget(0, Cols.West.ordinal(), w);
  }

  protected void add(final Widget w) {
    body.add(w);
  }

  protected void setTheme(final ThemeInfo t) {
    theme = t;
  }

  /** Get the history token for this screen. */
  public String getToken() {
    return token;
  }

  /** Set the history token for this screen. */
  public void setToken(final String t) {
    assert t != null && !t.isEmpty();
    token = t;

    if (isCurrentView()) {
      Gerrit.updateImpl(token);
    }
  }

  /**
   * If this view can display the given token, update it.
   *
   * @param newToken token the UI wants to show.
   * @return true if this view can show the token immediately, false if not.
   */
  public boolean displayToken(String newToken) {
    return false;
  }

  /** Set whether or not {@link Gerrit#isSignedIn()} must be true. */
  public final void setRequiresSignIn(final boolean b) {
    requiresSignIn = b;
  }

  /** Does {@link Gerrit#isSignedIn()} have to be true to be on this screen? */
  public final boolean isRequiresSignIn() {
    return requiresSignIn;
  }

  public void onShowView() {
    if (windowTitle != null) {
      Gerrit.setWindowTitle(this, windowTitle);
    }
    Gerrit.updateMenus(this);
    Gerrit.EVENT_BUS.fireEvent(new ScreenLoadEvent(this));
    Gerrit.setQueryString(null);
    registerKeys();

    if (theme != null) {
      Gerrit.THEMER.set(theme);
      setTheme = true;
    } else {
      Gerrit.THEMER.clear();
    }
  }
}
