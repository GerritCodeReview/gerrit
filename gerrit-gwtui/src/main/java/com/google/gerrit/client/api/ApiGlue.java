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

import com.google.gerrit.client.ErrorDialog;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gerrit.client.info.ServerInfo;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;

public class ApiGlue {
  private static String pluginName;

  public static void init() {
    init0();
    ActionContext.init();
    HtmlTemplate.init();
    Plugin.init();
  }

  private static native void init0() /*-{
    var serverUrl = @com.google.gwt.core.client.GWT::getHostPageBaseURL()();
    var ScreenDefinition = @com.google.gerrit.client.api.ExtensionScreen.Definition::TYPE;
    var SettingsScreenDefinition = @com.google.gerrit.client.api.ExtensionSettingsScreen.Definition::TYPE;
    var PanelDefinition = @com.google.gerrit.client.api.ExtensionPanel.Definition::TYPE;
    $wnd.Gerrit = {
      JsonString: @com.google.gerrit.client.rpc.NativeString::TYPE,
      events: {},
      plugins: {},
      screens: {},
      settingsScreens: {},
      panels: {},
      change_actions: {},
      edit_actions: {},
      revision_actions: {},
      project_actions: {},
      branch_actions: {},

      getPluginName: @com.google.gerrit.client.api.ApiGlue::getPluginName(),
      injectCss: @com.google.gwt.dom.client.StyleInjector::inject(Ljava/lang/String;),
      install: function (f) {
        var p = this._getPluginByUrl(@com.google.gerrit.client.api.PluginName::getCallerUrl()());
        @com.google.gerrit.client.api.ApiGlue::install(
            Lcom/google/gwt/core/client/JavaScriptObject;
            Lcom/google/gerrit/client/api/Plugin;)
          (f,p);
      },
      installGwt: function(u){return this._getPluginByUrl(u)},
      _getPluginByUrl: function(u) {
        return u.indexOf(serverUrl) == 0
          ? this.plugins[u.substring(serverUrl.length)]
          : this.plugins[u]
      },

      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: @com.google.gerrit.client.api.ApiGlue::refresh(),
      refreshMenuBar: @com.google.gerrit.client.api.ApiGlue::refreshMenuBar(),
      isSignedIn: @com.google.gerrit.client.api.ApiGlue::isSignedIn(),
      showError: @com.google.gerrit.client.api.ApiGlue::showError(Ljava/lang/String;),
      getServerInfo: @com.google.gerrit.client.api.ApiGlue::getServerInfo(),
      getCurrentUser: @com.google.gerrit.client.api.ApiGlue::getCurrentUser(),
      getUserPreferences: @com.google.gerrit.client.api.ApiGlue::getUserPreferences(),
      refreshUserPreferences: @com.google.gerrit.client.api.ApiGlue::refreshUserPreferences(),

      on: function (e,f){(this.events[e] || (this.events[e]=[])).push(f)},
      onAction: function (t,n,c){this._onAction(this.getPluginName(),t,n,c)},
      _onAction: function (p,t,n,c) {
        var i = p+'~'+n;
        if ('change' == t) this.change_actions[i]=c;
        else if ('edit' == t) this.edit_actions[i]=c;
        else if ('revision' == t) this.revision_actions[i]=c;
        else if ('project' == t) this.project_actions[i]=c;
        else if ('branch' == t) this.branch_actions[i]=c;
        else if ('screen' == t) _screen(p,t,c);
      },
      screen: function(r,c){this._screen(this.getPluginName(),r,c)},
      _screen: function(p,r,c){
        var s = new ScreenDefinition(r,c);
        (this.screens[p] || (this.screens[p]=[])).push(s);
      },
      settingsScreen: function(p,m,c){this._settingsScreen(this.getPluginName(),p,m,c)},
      _settingsScreen: function(n,p,m,c){
        var s = new SettingsScreenDefinition(p,m,c);
        (this.settingsScreens[n] || (this.settingsScreens[n]=[])).push(s);
      },
      panel: function(i,c){this._panel(this.getPluginName(),i,c)},
      _panel: function(n,i,c){
        var p = new PanelDefinition(n,c);
        (this.panels[i] || (this.panels[i]=[])).push(p);
      },

      url: function (d) {
        if (d && d.length > 0)
          return serverUrl + (d.charAt(0)=='/' ? d.substring(1) : d);
        return serverUrl;
      },

      _api: function(u) {
        return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(u);
      },
      get: function(u,b) {
        @com.google.gerrit.client.api.ActionContext::get(
            Lcom/google/gerrit/client/rpc/RestApi;
            Lcom/google/gwt/core/client/JavaScriptObject;)
          (this._api(u), b);
      },
      get_raw: function(u,b) {
        @com.google.gerrit.client.api.ActionContext::getRaw(
            Lcom/google/gerrit/client/rpc/RestApi;
            Lcom/google/gwt/core/client/JavaScriptObject;)
          (this._api(u), b);
      },
      post: function(u,i,b) {
        if (typeof i == 'string') {
          @com.google.gerrit.client.api.ActionContext::post(
              Lcom/google/gerrit/client/rpc/RestApi;
              Ljava/lang/String;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i, b);
        } else {
          @com.google.gerrit.client.api.ActionContext::post(
              Lcom/google/gerrit/client/rpc/RestApi;
              Lcom/google/gwt/core/client/JavaScriptObject;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i, b);
        }
      },
      post_raw: function(u,i,b) {
        if (typeof i == 'string') {
          @com.google.gerrit.client.api.ActionContext::postRaw(
              Lcom/google/gerrit/client/rpc/RestApi;
              Ljava/lang/String;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i, b);
        } else {
          @com.google.gerrit.client.api.ActionContext::postRaw(
              Lcom/google/gerrit/client/rpc/RestApi;
              Lcom/google/gwt/core/client/JavaScriptObject;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i, b);
        }
      },
      put: function(u,i,b) {
        if (b) {
          if (typeof i == 'string') {
            @com.google.gerrit.client.api.ActionContext::put(
                Lcom/google/gerrit/client/rpc/RestApi;
                Ljava/lang/String;
                Lcom/google/gwt/core/client/JavaScriptObject;)
              (this._api(u), i, b);
          } else {
            @com.google.gerrit.client.api.ActionContext::put(
                Lcom/google/gerrit/client/rpc/RestApi;
                Lcom/google/gwt/core/client/JavaScriptObject;
                Lcom/google/gwt/core/client/JavaScriptObject;)
              (this._api(u), i, b);
          }
        } else {
          @com.google.gerrit.client.api.ActionContext::put(
              Lcom/google/gerrit/client/rpc/RestApi;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i);
        }
      },
      put_raw: function(u,i,b) {
        if (b) {
          if (typeof i == 'string') {
            @com.google.gerrit.client.api.ActionContext::putRaw(
                Lcom/google/gerrit/client/rpc/RestApi;
                Ljava/lang/String;
                Lcom/google/gwt/core/client/JavaScriptObject;)
              (this._api(u), i, b);
          } else {
            @com.google.gerrit.client.api.ActionContext::putRaw(
                Lcom/google/gerrit/client/rpc/RestApi;
                Lcom/google/gwt/core/client/JavaScriptObject;
                Lcom/google/gwt/core/client/JavaScriptObject;)
              (this._api(u), i, b);
          }
        } else {
          @com.google.gerrit.client.api.ActionContext::putRaw(
              Lcom/google/gerrit/client/rpc/RestApi;
              Lcom/google/gwt/core/client/JavaScriptObject;)
            (this._api(u), i);
        }
      },
      'delete': function(u,b) {
        @com.google.gerrit.client.api.ActionContext::delete(
            Lcom/google/gerrit/client/rpc/RestApi;
            Lcom/google/gwt/core/client/JavaScriptObject;)
          (this._api(u), b);
      },
      del: function(u,b) {
        @com.google.gerrit.client.api.ActionContext::delete(
            Lcom/google/gerrit/client/rpc/RestApi;
            Lcom/google/gwt/core/client/JavaScriptObject;)
          (this._api(u), b);
      },
      del_raw: function(u,b) {
        @com.google.gerrit.client.api.ActionContext::deleteRaw(
            Lcom/google/gerrit/client/rpc/RestApi;
            Lcom/google/gwt/core/client/JavaScriptObject;)
          (this._api(u), b);
      },
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

  private static String getPluginName() {
    if (pluginName != null) {
      return pluginName;
    }
    return PluginName.fromUrl(PluginName.getCallerUrl());
  }

  private static void go(String urlOrToken) {
    if (urlOrToken.startsWith("http:")
        || urlOrToken.startsWith("https:")
        || urlOrToken.startsWith("//")) {
      Window.Location.assign(urlOrToken);
    } else {
      Gerrit.display(urlOrToken);
    }
  }

  private static void refresh() {
    Gerrit.display(History.getToken());
  }

  private static ServerInfo getServerInfo() {
    return Gerrit.info();
  }

  private static AccountInfo getCurrentUser() {
    return Gerrit.getUserAccount();
  }

  private static GeneralPreferences getUserPreferences() {
    return Gerrit.getUserPreferences();
  }

  private static void refreshUserPreferences() {
    Gerrit.refreshUserPreferences();
  }

  private static void refreshMenuBar() {
    Gerrit.refreshMenuBar();
  }

  private static boolean isSignedIn() {
    return Gerrit.isSignedIn();
  }

  private static void showError(String message) {
    new ErrorDialog(message).center();
  }

  static final native void invoke(JavaScriptObject f) /*-{ f(); }-*/;

  static final native void invoke(JavaScriptObject f, JavaScriptObject a) /*-{ f(a); }-*/;

  static final native void invoke(
      JavaScriptObject f, JavaScriptObject a, JavaScriptObject b) /*-{ f(a,b) }-*/;

  static final native void invoke(JavaScriptObject f, String a) /*-{ f(a); }-*/;

  public static final void fireEvent(String event, String a) {
    JsArray<JavaScriptObject> h = getEventHandlers(event);
    for (int i = 0; i < h.length(); i++) {
      invoke(h.get(i), a);
    }
  }

  public static final void fireEvent(String event, Element e) {
    JsArray<JavaScriptObject> h = getEventHandlers(event);
    for (int i = 0; i < h.length(); i++) {
      invoke(h.get(i), e);
    }
  }

  static final void fireEvent(String event, JavaScriptObject a, JavaScriptObject b) {
    JsArray<JavaScriptObject> h = getEventHandlers(event);
    for (int i = 0; i < h.length(); i++) {
      invoke(h.get(i), a, b);
    }
  }

  static final native JsArray<JavaScriptObject> getEventHandlers(String e)
      /*-{ return $wnd.Gerrit.events[e] || [] }-*/ ;

  private ApiGlue() {}
}
