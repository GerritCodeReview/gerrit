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

import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.dom.client.Element;

/** Screen contributed by a plugin. */
public class ExtensionScreen extends Screen {
  private Context ctx;

  public ExtensionScreen(String token) {
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
      JsArrayString m = def.match(rest);
      if (m != null) {
        return Context.create(def, this, m);
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
    ctx.onUnload();
  }

  static class Definition extends JavaScriptObject {
    static native JsArray<Definition> get(String n)
    /*-{ return $wnd.Gerrit.screens[n]||[] }-*/;

    final native JsArrayString match(String t)
    /*-{
      var p = this.pattern;
      if (p instanceof $wnd.RegExp) {
        var m = p.exec(t);
        return m && m[0] == t ? m : null;
      }
      return p == t ? [t] : null;
    }-*/;

    protected Definition() {
    }
  }

  static class Context extends JavaScriptObject {
    static final Context create(
        Definition def,
        ExtensionScreen view,
        JsArrayString match) {
      return create0(TYPE, def, view, view.getBody().getElement(), match);
    }

    private static final JavaScriptObject TYPE = init0();
    private static final native Context create0(
        JavaScriptObject T,
        Definition d,
        ExtensionScreen v,
        Element e,
        JsArrayString m)
    /*-{ return new T(d,v,e,m) }-*/;

    final native void onLoad() /*-{ this._d.onLoad(this) }-*/;
    final native void onUnload() /*-{ this._u() }-*/;

    private static final native JavaScriptObject init0() /*-{
      var T = function(d,v,e,m) {
        this._d = d;
        this._v = v;
        this.body = e;
        this.token = m[0];
        this.token_match = m;
      };
      T.prototype = {
        doc: $wnd.document,
        setTitle: function(t){this._v.@com.google.gerrit.client.ui.Screen::setPageTitle(Ljava/lang/String;)(t)},
        setWindowTitle: function(t){this._v.@com.google.gerrit.client.ui.Screen::setWindowTitle(Ljava/lang/String;)(t)},
        show: function(){$entry(this._v.@com.google.gwtexpui.user.client.View::display()())},
        onUnload: function(f){this._u=f},
        _u: function(){},
      };
      return T;
    }-*/;

    protected Context() {
    }
  }
}
