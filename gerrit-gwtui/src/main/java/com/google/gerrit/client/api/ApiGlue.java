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

import com.google.gerrit.client.rpc.NativeMap;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;

public class ApiGlue {
  private static String pluginName;

  public static void init(){
    init0(
        ChangeGlue.actions,
        RevisionGlue.actions);
  }

  private static native void init0(
      NativeMap<JavaScriptObject> changes,
      NativeMap<JavaScriptObject> revisions)
  /*-{
    var pn = $entry(@com.google.gerrit.client.api.ApiGlue::getPluginName());
    function _i(i){return pn()+'~'+i};

    var G = {
      install: $entry(@com.google.gerrit.client.api.ApiGlue::install(Lcom/google/gwt/core/client/JavaScriptObject;)),
      getPluginName: pn,

      onChangeAction: function(n,c){changes[_i(n)]=c},
      onRevisionAction: function(n,c){revisions[_i(n)]=c},
    };
    G.RpcContext = function(u){this._u=u};
    G.RpcContext.prototype = {
      call: function(i,b){
        var m = this.action.method.toLowerCase();
        if (i == null) this[m](b);
        else this[m](i,b);
      },
      get: function(b){@com.google.gerrit.client.api.ActionContext::get(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,b)},
      post: function(i,b){@com.google.gerrit.client.api.ActionContext::post(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,i,b)},
      put: function(i,b){@com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,i,b)},
      'delete': function(b){@com.google.gerrit.client.api.ActionContext::delete(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,b)},
    };
    $wnd.Gerrit = G;
  }-*/;

  private static void install(JavaScriptObject cb) {
    try {
      pluginName = getPluginName();
      invoke(cb);
    } finally {
      pluginName = null;
    }
  }

  private static final String getPluginName() {
    if (pluginName != null) {
      return pluginName;
    }
    try {
      throw new Exception();
    } catch (Exception err) {
      String baseUrl = GWT.getHostPageBaseURL() + "plugins/";
      for (StackTraceElement e : err.getStackTrace()) {
        String u = e.getFileName();
        if (u.startsWith(baseUrl)) {
          int s = u.indexOf('/', baseUrl.length());
          if (s > 0) {
            return u.substring(baseUrl.length(), s);
          }
        }
      }
    }
    return "<unknown>";
  }

  static final native void invoke(JavaScriptObject f) /*-{ f(); }-*/;
  static final native void invoke(JavaScriptObject f, JavaScriptObject a) /*-{ f(a); }-*/;
  static final native void invoke(JavaScriptObject f, String a) /*-{ f(a); }-*/;

  private ApiGlue() {
  }
}
