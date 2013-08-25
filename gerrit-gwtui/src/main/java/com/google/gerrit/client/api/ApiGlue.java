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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;

public class ApiGlue {
  private static String pluginName;

  public static void init() {
    init0();
    ActionContext.init();
  }

  private static native void init0() /*-{
    var serverUrl = @com.google.gwt.core.client.GWT::getHostPageBaseURL()();
    var Plugin = function (name){this.name = name};
    var Gerrit = {
      getPluginName: @com.google.gerrit.client.api.ApiGlue::getPluginName(),
      install: function (f) {
        var p = new Plugin(this.getPluginName());
        @com.google.gerrit.client.api.ApiGlue::install(Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(f,p);
      },

      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: @com.google.gerrit.client.api.ApiGlue::refresh(),

      change_actions: {},
      revision_actions: {},
      project_actions: {},
      onAction: function (t,n,c){this._onAction(this.getPluginName(),t,n,c)},
      _onAction: function (p,t,n,c) {
        var i = p+'~'+n;
        if ('change' == t) this.change_actions[i]=c;
        else if ('revision' == t) this.revision_actions[i]=c;
        else if ('project' == t) this.project_actions[i]=c;
      },

      url: function (d) {
        if (d && d.length > 0)
          return serverUrl + (d.charAt(0)=='/' ? d.substring(1) : d);
        return serverUrl;
      },

      _api: function(u) {return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(u)},
      get: function(u,b){@com.google.gerrit.client.api.ActionContext::get(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
      post: function(u,i,b){@com.google.gerrit.client.api.ActionContext::post(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b)},
      put: function(u,i,b){@com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b)},
      'delete': function(u,b){@com.google.gerrit.client.api.ActionContext::delete(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
    };

    Plugin.prototype = {
      getPluginName: function(){return this.name},
      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: Gerrit.refresh,
      onAction: function(t,n,c) {Gerrit._onAction(this.name,t,n,c)},

      url: function (d) {
        var u = serverUrl + 'plugins/' + this.name + '/';
        if (d && d.length > 0) u += d.charAt(0)=='/' ? d.substring(1) : d;
        return u;
      },

      _api: function(d) {
        var u = 'plugins/' + this.name + '/';
        if (d && d.length > 0) u += d.charAt(0)=='/' ? d.substring(1) : d;
        return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(u);
      },

      get: function(u,b){@com.google.gerrit.client.api.ActionContext::get(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
      post: function(u,i,b){@com.google.gerrit.client.api.ActionContext::post(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b)},
      put: function(u,i,b){@com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b)},
      'delete': function(u,b){@com.google.gerrit.client.api.ActionContext::delete(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
    };

    $wnd.Gerrit = Gerrit;
  }-*/;

  private static void install(JavaScriptObject cb, JavaScriptObject p) {
    try {
      pluginName = PluginName.get();
      invoke(cb, p);
    } finally {
      pluginName = null;
    }
  }

  private static final String getPluginName() {
    return pluginName != null ? pluginName : PluginName.get();
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
  static final native void invoke(JavaScriptObject f, String a) /*-{ f(a); }-*/;

  private ApiGlue() {
  }
}
