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

package com.google.gerrit.plugin.client.screen;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/** Screen contributed by this plugin. */
public final class Screen extends SimplePanel {
  /** Initializes a screen for display. */
  public interface Callback {
    /**
     * Invoked when the screen has been created, but not yet displayed.
     * <p>
     * The implementation should create a single widget to define the content of
     * this screen and added it to the passed screen instance. When the screen
     * is ready to be displayed, call {@link Screen#show()}.
     *
     * @param screen panel that will contain the screen widget.
     */
    public void onLoad(Screen screen);
  }

  static class Context extends JavaScriptObject {
    final native Element body() /*-{ return this.body }-*/;
    final native String token() /*-{ return this.token }-*/;
    final native JsArrayString token_match() /*-{ return this.token_match }-*/;
    final native void show() /*-{ this.show() }-*/;
    final native void setTitle(String t) /*-{ this.setTitle(t) }-*/;
    final native void setWindowTitle(String t) /*-{ this.setWindowTitle(t) }-*/;
    final native void onUnload(Runnable r) /*-{
      this.onUnload($entry(r.@java.lang.Runnable::run()()));
    }-*/;

    protected Context() {
    }
  }

  private final Context ctx;

  Screen(Context ctx) {
    super(ctx.body());
    this.ctx = ctx;
    onAttach();
    ctx.onUnload(new Runnable() {
      @Override
      public void run() {
        onDetach();
      }
    });
  }

  public final String getToken() {
    return ctx.token();
  }

  public final String getToken(int group) {
    return ctx.token_match().get(group);
  }

  public final int getTokenGroups() {
    return ctx.token_match().length();
  }

  /**
   * Set the page title text; appears above the widget.
   *
   * @param titleText text to display above the widget.
   */
  public final void setPageTitle(String titleText) {
    ctx.setTitle(titleText);
  }

  /**
   * Set the window title text; appears in the browser window title bar.
   *
   * @param titleText text to display in the window title bar.
   */
  public final void setWindowTitle(String titleText) {
    ctx.setWindowTitle(titleText);
  }

  /**
   * Add the widget and immediately show the screen.
   *
   * @param w child containing the content.
   */
  public final void show(Widget w) {
    add(w);
    show();
  }

  /** Show this screen in the web interface. */
  public final void show() {
    ctx.show();
  }

  /** Synonym for {@link #show()}. */
  public final void display() {
    show();
  }
}
