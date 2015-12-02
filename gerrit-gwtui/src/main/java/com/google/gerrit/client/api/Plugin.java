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

import com.google.gwt.core.client.JavaScriptObject;

final class Plugin extends JavaScriptObject {
  private static final JavaScriptObject TYPE = createType();

  static Plugin create(String url) {
    int s = "plugins/".length();
    int e = url.indexOf('/', s);
    String name = url.substring(s, e);
    return create(TYPE, url, name);
  }

  final native String url() /*-{ return this._scriptUrl }-*/;
  final native String name() /*-{ return this.name }-*/;

  final native boolean loaded() /*-{ return this._success || this._failure != null }-*/;
  final native Exception failure() /*-{ return this._failure }-*/;
  final native void failure(Exception e) /*-{ this._failure = e }-*/;
  final native boolean success() /*-{ return this._success || false }-*/;
  final native void _initialized() /*-{ this._success = true }-*/;

  private static native Plugin create(JavaScriptObject T, String u, String n)
  /*-{ return new T(u,n) }-*/;

  private static native JavaScriptObject createType() /*-{
    function Plugin(u, n) {
      this._scriptUrl = u;
      this.name = n;
    }
    return Plugin;
  }-*/;

  static native void init() /*-{
    var G = $wnd.Gerrit;
    @com.google.gerrit.client.api.Plugin::TYPE.prototype = {
      getPluginName: function(){return this.name},
      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: @com.google.gerrit.client.api.ApiGlue::refresh(),
      on: function(e,f){G.on(e,f)},
      screen: function(p,c){G._screen(this.name,p,c)},
      infoRow: function(i,h,a,c){G._infoRow(this.name,i,h,a,c)},

      url: function (u){return G.url(this._url(u))},

      _loadedGwt: function(){@com.google.gerrit.client.api.PluginLoader::loaded()()},
      _api: function(u){return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(this._url(u))},
      _url: function (d) {
        var u = 'plugins/' + this.name + '/';
        if (d && d.length > 0)
          return u + (d.charAt(0)=='/' ? d.substring(1) : d);
        return u;
      },
    };
  }-*/;

  protected Plugin() {
  }
}
