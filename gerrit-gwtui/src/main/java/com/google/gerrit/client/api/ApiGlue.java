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
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

public class ApiGlue {
  private static String pluginName;

  public static void init() {
    init0();
    ActionContext.init();
    addHistoryHook();
  }

  private static native void init0() /*-{
    var serverUrl = @com.google.gwt.core.client.GWT::getHostPageBaseURL()();
    var Plugin = function (name){this.name = name};
    var ScreenDefinition = function (r,c) {this.pattern = r;this.onLoad = c;};
    var Gerrit = {
      getPluginName: @com.google.gerrit.client.api.ApiGlue::getPluginName(),
      install: function (f) {
        var p = new Plugin(this.getPluginName());
        @com.google.gerrit.client.api.ApiGlue::install(Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gerrit/client/api/JsUiPlugin;)(f,p);
      },

      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      refresh: @com.google.gerrit.client.api.ApiGlue::refresh(),

      events: {},
      screens: {},
      change_actions: {},
      revision_actions: {},
      project_actions: {},

      on: function (e,f){(this.events[e] || (this.events[e]=[])).push(f)},
      onAction: function (t,n,c){this._onAction(this.getPluginName(),t,n,c)},
      _onAction: function (p,t,n,c) {
        var i = p+'~'+n;
        if ('change' == t) this.change_actions[i]=c;
        else if ('revision' == t) this.revision_actions[i]=c;
        else if ('project' == t) this.project_actions[i]=c;
        else if ('screen' == t) _screen(p,t,c);
      },
      screen: function(r,c){this._screen(this.getPluginName(),r,c)},
      _screen: function(p,r,c){
        var s = new ScreenDefinition(r,c);
        (this.screens[p] || (this.screens[p]=[])).push(s);
      },

      url: function (d) {
        if (d && d.length > 0)
          return serverUrl + (d.charAt(0)=='/' ? d.substring(1) : d);
        return serverUrl;
      },

      _api: function(u) {return @com.google.gerrit.client.rpc.RestApi::new(Ljava/lang/String;)(u)},
      get: function(u,b){@com.google.gerrit.client.api.ActionContext::get(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
      post: function(u,i,b){
        if (typeof i=='string')
          @com.google.gerrit.client.api.ActionContext::post(Lcom/google/gerrit/client/rpc/RestApi;Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b);
        else
          @com.google.gerrit.client.api.ActionContext::post(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b);
      },
      put: function(u,i,b){
        if(b){
          if(typeof i=='string')
            @com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b);
          else
            @com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i,b);
        }else{
          @com.google.gerrit.client.api.ActionContext::put(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),i)
        }
      },
      'delete': function(u,b){@com.google.gerrit.client.api.ActionContext::delete(Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._api(u),b)},
      JsonString: @com.google.gerrit.client.rpc.NativeString::TYPE,

      css: @com.google.gerrit.client.api.ApiGlue::css(Ljava/lang/String;),
      html: function(s,o,w) {
        var i = {};
        if (o) {
          s = s.replace(/\sid=['"]\{([a-z_][a-z0-9_]*)\}['"]|\{([a-z0-9._-]+)\}/gi, function(m,a,b) {
            if (a)
              return @com.google.gerrit.client.api.ApiGlue::id(Lcom/google/gwt/core/client/JavaScriptObject;Ljava/lang/String;)(i,a)
            return @com.google.gerrit.client.api.ApiGlue::html(Lcom/google/gwt/core/client/JavaScriptObject;Ljava/lang/String;)(o,b);
          });
        }
        s = @com.google.gerrit.client.api.ApiGlue::parseHtml(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;Z)(s,i,o,!!w);
        return w?{root:s,elements:i}:s;
      },
      injectCss: @com.google.gwt.dom.client.StyleInjector::inject(Ljava/lang/String;),
    };

    Plugin.prototype = {
      getPluginName: function(){return this.name},
      go: @com.google.gerrit.client.api.ApiGlue::go(Ljava/lang/String;),
      on: Gerrit.on,
      onAction: function(t,n,c) {Gerrit._onAction(this.name,t,n,c)},
      refresh: Gerrit.refresh,
      screen: function(r,c) {Gerrit._screen(this.name,r,c)},

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

  /** Install deprecated {@code gerrit_addHistoryHook()} function. */
  private static native void addHistoryHook() /*-{
    $wnd.gerrit_addHistoryHook = function(h) {
      var p = @com.google.gwt.user.client.Window.Location::getPath()();
      $wnd.Gerrit.on('history', function(t) { h(p + "#" + t) })
     };
  }-*/;

  private static void install(JavaScriptObject cb, JsUiPlugin p) {
    try {
      pluginName = p.name();
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

  private static final String css(String css) {
    String name = DOM.createUniqueId();
    StyleInjector.inject("." + name + "{" + css + "}");
    return name;
  }

  private static final String id(JavaScriptObject idMap, String key) {
    String id = DOM.createUniqueId();
    set(idMap, key, id);
    return " id='" + id + "'";
  }

  private static native void set(JavaScriptObject m, String k, String v)
  /*-{ m[k] = v }-*/;

  private static native void set(JavaScriptObject m, String k, JavaScriptObject v)
  /*-{ m[k] = v }-*/;

  private static final String html(JavaScriptObject obj, String id) {
    int d = id.indexOf('.');
    if (0 < d) {
      String n = id.substring(0, d);
      return html(obj(obj, n), id.substring(d + 1));
    }
    return new SafeHtmlBuilder().append(str(obj, id)).asString();
  }

  private static native JavaScriptObject obj(JavaScriptObject o, String n)
  /*-{ return o[n] }-*/;

  private static native String str(JavaScriptObject o, String n)
  /*-{ return ''+o[n] }-*/;

  private static native String get(JavaScriptObject o, String n)
  /*-{ return o[n] }-*/;

  private static final Node parseHtml(
      String html,
      JavaScriptObject idMap,
      JavaScriptObject valueMap,
      boolean wantElements) {
    Element div = Document.get().createDivElement();
    div.setInnerHTML(html);
    for (String key : Natives.keys(idMap)) {
      attachHandlers(div,
          get(idMap, key),
          obj(valueMap, key),
          key,
          wantElements ? idMap : null);
    }
    if (div.getChildCount() == 1) {
      return div.getFirstChild();
    }
    return div;
  }

  private static void attachHandlers(
      Element e,
      String id,
      JavaScriptObject obj,
      String key,
      JavaScriptObject idMap) {
    if (id.equals(e.getId())) {
      e.setId(null);
      attachHandlers(e, obj);
      if (idMap != null) {
        set(idMap, key, e);
      }
    }
    for(Element c = e.getFirstChildElement(); c != null;) {
      attachHandlers(c, id, obj, key, idMap);
      c = c.getNextSiblingElement();
    }
  }

  private static native void attachHandlers(Element e, JavaScriptObject o) /*-{
    for (var k in o) {
      var f = o[k];
      if (k.substring(0, 2) == 'on' && typeof f == 'function')
        e[k] = f;
    }
  }-*/;

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
