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

package com.google.gerrit.client.api;

import com.google.gerrit.client.account.SettingsScreen;
import com.google.gerrit.client.rpc.NativeMap;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import java.util.Set;

/** SettingsScreen contributed by a plugin. */
public class ExtensionSettingsScreen extends SettingsScreen {
  private Context ctx;

  public ExtensionSettingsScreen(String token) {
    if (token.contains("?")) {
      token = token.substring(0, token.indexOf('?'));
    }
    String name;
    String rest;
    int s = token.indexOf('/');
    if (0 < s) {
      name = token.substring(0, s);
      rest = token.substring(s + 1);
    } else {
      name = token;
      rest = "";
    }
    ctx = create(name, rest);
  }

  private Context create(String name, String rest) {
    for (Definition def : Natives.asList(Definition.get(name))) {
      if (def.matches(rest)) {
        return Context.create(def, this);
      }
    }
    return null;
  }

  public boolean isFound() {
    return ctx != null;
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setHeaderVisible(false);
    ctx.onLoad();
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    for (JavaScriptObject u : Natives.asList(ctx.unload())) {
      ApiGlue.invoke(u);
    }
  }

  public static class Definition extends JavaScriptObject {
    static final JavaScriptObject TYPE = init();

    private static native JavaScriptObject init() /*-{
      function SettingsScreenDefinition(p, m, c) {
        this.path = p;
        this.menu = m;
        this.onLoad = c;
      };
      return SettingsScreenDefinition;
    }-*/;

    public static native JsArray<Definition> get(String n)
        /*-{ return $wnd.Gerrit.settingsScreens[n] || [] }-*/ ;

    public static final Set<String> plugins() {
      return Natives.keys(settingsScreens());
    }

    private static native NativeMap<NativeString> settingsScreens()
        /*-{ return $wnd.Gerrit.settingsScreens; }-*/ ;

    public final native String getPath() /*-{ return this.path; }-*/;

    public final native String getMenu() /*-{ return this.menu; }-*/;

    final native boolean matches(String t) /*-{ return this.path == t; }-*/;

    protected Definition() {}
  }

  static class Context extends JavaScriptObject {
    static final Context create(Definition def, ExtensionSettingsScreen view) {
      return create(TYPE, def, view, view.getBody().getElement());
    }

    final native void onLoad() /*-{ this._d.onLoad(this) }-*/;

    final native JsArray<JavaScriptObject> unload() /*-{ return this._u }-*/;

    private static native Context create(
        JavaScriptObject T, Definition d, ExtensionSettingsScreen s, Element e)
        /*-{ return new T(d,s,e) }-*/ ;

    private static final JavaScriptObject TYPE = init();

    private static native JavaScriptObject init() /*-{
      var T = function(d,s,e) {
        this._d = d;
        this._s = s;
        this._u = [];
        this.body = e;
      };
      T.prototype = {
        setTitle: function(t){this._s.@com.google.gerrit.client.ui.Screen::setPageTitle(Ljava/lang/String;)(t)},
        setWindowTitle: function(t){this._s.@com.google.gerrit.client.ui.Screen::setWindowTitle(Ljava/lang/String;)(t)},
        show: function(){$entry(this._s.@com.google.gwtexpui.user.client.View::display()())},
        onUnload: function(f){this._u.push(f)},
      };
      return T;
    }-*/;

    protected Context() {}
  }
}
