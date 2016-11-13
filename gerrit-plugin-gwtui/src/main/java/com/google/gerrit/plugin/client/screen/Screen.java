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

/**
 * Screen contributed by this plugin.
 *
 * <p>Screens should be registered early at module load:
 *
 * <pre>
 * &#064;Override
 * public void onModuleLoad() {
 *   Plugin.get().screen(&quot;hi&quot;, new Screen.EntryPoint() {
 *     &#064;Override
 *     public void onLoad(Screen screen) {
 *       screen.setPageTitle(&quot;Hi&quot;);
 *       screen.show(new Label(&quot;World&quot;));
 *     }
 *   });
 * }
 * </pre>
 */
public final class Screen extends SimplePanel {
  /** Initializes a screen for display. */
  public interface EntryPoint {
    /**
     * Invoked when the screen has been created, but not yet displayed.
     *
     * <p>The implementation should create a single widget to define the content of this screen and
     * added it to the passed screen instance. When the screen is ready to be displayed, call {@link
     * Screen#show()}.
     *
     * <p>To use multiple widgets, compose them in panels such as {@code FlowPanel} and add only the
     * top level widget to the screen.
     *
     * <p>The screen is already attached to the browser DOM in an invisible area. Any widgets added
     * to the screen will immediately receive {@code onLoad()}. GWT will fire {@code onUnload()}
     * when the screen is removed from the UI, generally caused by the user navigating to another
     * screen.
     *
     * @param screen panel that will contain the screen widget.
     */
    void onLoad(Screen screen);
  }

  static final class Context extends JavaScriptObject {
    native Element body() /*-{ return this.body }-*/;

    native JsArrayString token_match() /*-{ return this.token_match }-*/;

    native void show() /*-{ this.show() }-*/;

    native void setTitle(String t) /*-{ this.setTitle(t) }-*/;

    native void setWindowTitle(String t) /*-{ this.setWindowTitle(t) }-*/;

    native void detach(Screen s) /*-{
      this.onUnload($entry(function(){
        s.@com.google.gwt.user.client.ui.Widget::onDetach()();
      }));
    }-*/;

    protected Context() {}
  }

  private final Context ctx;

  Screen(Context ctx) {
    super(ctx.body());
    this.ctx = ctx;
    onAttach();
    ctx.detach(this);
  }

  /** @return the token suffix after {@code "/#/x/plugin-name/"}. */
  public String getToken() {
    return getToken(0);
  }

  /**
   * @param group groups range from 1 to {@code getTokenGroups() - 1}. Token group 0 is the entire
   *     token, see {@link #getToken()}.
   * @return the token from the regex match group.
   */
  public String getToken(int group) {
    return ctx.token_match().get(group);
  }

  /** @return total number of token groups. */
  public int getTokenGroups() {
    return ctx.token_match().length();
  }

  /**
   * Set the page title text; appears above the widget.
   *
   * @param titleText text to display above the widget.
   */
  public void setPageTitle(String titleText) {
    ctx.setTitle(titleText);
  }

  /**
   * Set the window title text; appears in the browser window title bar.
   *
   * @param titleText text to display in the window title bar.
   */
  public void setWindowTitle(String titleText) {
    ctx.setWindowTitle(titleText);
  }

  /**
   * Add the widget and immediately show the screen.
   *
   * @param w child containing the content.
   */
  public void show(Widget w) {
    setWidget(w);
    ctx.show();
  }

  /** Show this screen in the web interface. */
  public void show() {
    ctx.show();
  }
}
