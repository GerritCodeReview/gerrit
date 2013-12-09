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

package com.google.gerrit.client.api;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;

public class ApiGlue {
  private static String pluginName;

  public static void init() {
    init0(GWT.getHostPageBaseURL(), NativeString.TYPE);
    Plugin.init();
    addHistoryHook();
  }

  private static native void init0(String serverUrl, JavaScriptObject JsonString) /*-{
    var InfoRowDefinition = @com.google.gerrit.client.api.ExtensionRows.Definition::TYPE;
    $wnd.Gerrit = {
      JsonString: JsonString,
      events: {},
      plugins: {},
      infoRows: {},

      getPluginName: @com.google.gerrit.client.api.ApiGlue::getPluginName(),
      injectCss: @com.google.gwt.dom.client.StyleInjector::inject(Ljava/lang/String;),
      install: function (f) {
        var p = this._getPluginByUrl(@com.google.gerrit.client.api.PluginName::getCallerUrl()());
        @com.google.gerrit.client.api.ApiGlue::install(Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gerrit/client/api/Plugin;)(f,p);
      },
      installGwt: function(u){return this._getPluginByUrl(u)},
      _getPluginByUrl: function(u) {
        return u.indexOf(serverUrl) == 0
          ? this.plugins[u.substring(serverUrl.length)]
          : this.plugins[u]
      },

      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: @com.google.gerrit.client.api.ApiGlue::refresh(),

      on: function (e,f){(this.events[e] || (this.events[e]=[])).push(f)},

      url: function (d) {
        if (d && d.length > 0)
          return serverUrl + (d.charAt(0)=='/' ? d.substring(1) : d);
        return serverUrl;
      },

      infoRow: function(i,h,c){this._infoRow(this.getPluginName(),i,h,c)},
      _infoRow: function(n,i,h,c){
        var r = new InfoRowDefinition(n,h,c);
        (this.infoRows[i] || (this.infoRows[i]=[])).push(r);
      },

      _api: function(u) {return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(u)},
    };
  }-*/;

  /** Install deprecated {@code gerrit_addHistoryHook()} function. */
  private static native void addHistoryHook() /*-{
    $wnd.gerrit_addHistoryHook = function(h) {
      var p = @com.google.gwt.user.client.Window.Location::getPath()();
      $wnd.Gerrit.on('history', function(t) { h(p + "#" + t) })
     };
  }-*/;

  private static void install(JavaScriptObject cb, Plugin p) throws Exception {
    try {
      pluginName = p.name();
      invoke(cb, p);
      p._initialized();
    } catch (Exception e) {
      p.failure(e);
      throw e;
    } finally {
      pluginName = null;
      PluginLoader.loaded();
    }
  }

  private static final String getPluginName() {
    if (pluginName != null) {
      return pluginName;
    }
    return PluginName.fromUrl(PluginName.getCallerUrl());
  }

  private static final void go(String urlOrToken) {
    if (urlOrToken.startsWith("http:")
        || urlOrToken.startsWith("https:")
        || urlOrToken.startsWith("//")) {
      Window.Location.assign(urlOrToken);
    } else {
      Gerrit.display(urlOrToken);
    }
  }

  private static final void refresh() {
    Gerrit.display(History.getToken());
  }

  static final native void invoke(JavaScriptObject f) /*-{ f(); }-*/;
  static final native void invoke(JavaScriptObject f, JavaScriptObject a) /*-{ f(a); }-*/;
  static final native void invoke(JavaScriptObject f, JavaScriptObject a, JavaScriptObject b) /*-{ f(a,b) }-*/;
  static final native void invoke(JavaScriptObject f, String a) /*-{ f(a); }-*/;

  public static final void fireEvent(String event, String a) {
    JsArray<JavaScriptObject> h = getEventHandlers(event);
    for (int i = 0; i < h.length(); i++) {
      invoke(h.get(i), a);
    }
  }

  static final void fireEvent(String event, JavaScriptObject a, JavaScriptObject b) {
    JsArray<JavaScriptObject> h = getEventHandlers(event);
    for (int i = 0; i < h.length(); i++) {
      invoke(h.get(i), a, b);
    }
  }

  static final native JsArray<JavaScriptObject> getEventHandlers(String e)
  /*-{ return $wnd.Gerrit.events[e] || [] }-*/;

  private ApiGlue() {
  }
}
