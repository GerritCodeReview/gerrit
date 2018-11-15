// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.plugin.client.extension;

import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.SimplePanel;

/**
 * Panel that extends a Gerrit core screen contributed by this plugin.
 *
 * <p>Panel should be registered early at module load:
 *
 * <pre>
 * &#064;Override
 * public void onModuleLoad() {
 *   Plugin.get().panel(GerritUiExtensionPoint.CHANGE_SCREEN_BELOW_CHANGE_INFO_BLOCK,
 *       new Panel.EntryPoint() {
 *         &#064;Override
 *         public void onLoad(Panel panel) {
 *           panel.setWidget(new Label(&quot;World&quot;));
 *         }
 *       });
 * }
 * </pre>
 */
public class Panel extends SimplePanel {
  /** Initializes a panel for display. */
  public interface EntryPoint {
    /**
     * Invoked when the panel has been created.
     *
     * <p>The implementation should create a single widget to define the content of this panel and
     * add it to the passed panel instance.
     *
     * <p>To use multiple widgets, compose them in panels such as {@code FlowPanel} and add only the
     * top level widget to the panel.
     *
     * <p>The panel is already attached to the browser DOM. Any widgets added to the screen will
     * immediately receive {@code onLoad()}. GWT will fire {@code onUnload()} when the panel is
     * removed from the UI, generally caused by the user navigating to another screen.
     *
     * @param panel panel that will contain the panel widget.
     */
    void onLoad(Panel panel);
  }

  static final class Context extends JavaScriptObject {
    native Element body() /*-{ return this.body }-*/;

    native String get(String k) /*-{ return this.p[k]; }-*/;

    native int getInt(String k, int d) /*-{
      return this.p.hasOwnProperty(k) ? this.p[k] : d
    }-*/;

    native int getBoolean(String k, boolean d) /*-{
      return this.p.hasOwnProperty(k) ? this.p[k] : d
    }-*/;

    native JavaScriptObject getObject(String k) /*-{ return this.p[k]; }-*/;

    native void detach(Panel p) /*-{
      this.onUnload($entry(function(){
        p.@com.google.gwt.user.client.ui.Widget::onDetach()();
      }));
    }-*/;

    protected Context() {}
  }

  private final Context ctx;

  Panel(Context ctx) {
    super(ctx.body());
    this.ctx = ctx;
    onAttach();
    ctx.detach(this);
  }

  public String get(GerritUiExtensionPoint.Key key) {
    return ctx.get(key.name());
  }

  public int getInt(GerritUiExtensionPoint.Key key, int defaultValue) {
    return ctx.getInt(key.name(), defaultValue);
  }

  public int getBoolean(GerritUiExtensionPoint.Key key, boolean defaultValue) {
    return ctx.getBoolean(key.name(), defaultValue);
  }

  public JavaScriptObject getObject(GerritUiExtensionPoint.Key key) {
    return ctx.getObject(key.name());
  }
}
