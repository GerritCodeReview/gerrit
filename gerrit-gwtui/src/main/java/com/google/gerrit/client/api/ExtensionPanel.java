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

import com.google.gerrit.client.GerritUiExtensionPoint;
import com.google.gerrit.client.rpc.Natives;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExtensionPanel extends FlowPanel {
  private static final Logger logger = Logger.getLogger(ExtensionPanel.class.getName());
  private final GerritUiExtensionPoint extensionPoint;
  private final List<Context> contexts;

  public ExtensionPanel(GerritUiExtensionPoint extensionPoint) {
    this.extensionPoint = extensionPoint;
    this.contexts = create();
  }

  private List<Context> create() {
    List<Context> contexts = new ArrayList<>();
    for (Definition def : Natives.asList(Definition.get(extensionPoint.name()))) {
      SimplePanel p = new SimplePanel();
      add(p);
      contexts.add(Context.create(def, p));
    }
    return contexts;
  }

  public void put(GerritUiExtensionPoint.Key key, String value) {
    for (Context ctx : contexts) {
      ctx.put(key.name(), value);
    }
  }

  public void putInt(GerritUiExtensionPoint.Key key, int value) {
    for (Context ctx : contexts) {
      ctx.putInt(key.name(), value);
    }
  }

  public void putBoolean(GerritUiExtensionPoint.Key key, boolean value) {
    for (Context ctx : contexts) {
      ctx.putBoolean(key.name(), value);
    }
  }

  public void putObject(GerritUiExtensionPoint.Key key, JavaScriptObject value) {
    for (Context ctx : contexts) {
      ctx.putObject(key.name(), value);
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    for (Context ctx : contexts) {
      try {
        ctx.onLoad();
      } catch (RuntimeException e) {
        logger.log(
            Level.SEVERE,
            "Failed to load extension panel for extension point "
                + extensionPoint.name()
                + " from plugin "
                + ctx.getPluginName()
                + ": "
                + e.getMessage());
      }
    }
  }

  @Override
  protected void onUnload() {
    super.onUnload();
    for (Context ctx : contexts) {
      for (JavaScriptObject u : Natives.asList(ctx.unload())) {
        ApiGlue.invoke(u);
      }
    }
  }

  static class Definition extends JavaScriptObject {
    static final JavaScriptObject TYPE = init();

    private static native JavaScriptObject init() /*-{
      function PanelDefinition(n, c) {
        this.pluginName = n;
        this.onLoad = c;
      };
      return PanelDefinition;
    }-*/;

    static native JsArray<Definition> get(String i)/*-{ return $wnd.Gerrit.panels[i] || [] }-*/ ;

    protected Definition() {}
  }

  static class Context extends JavaScriptObject {
    static final Context create(Definition def, SimplePanel panel) {
      return create(TYPE, def, panel.getElement());
    }

    final native void onLoad() /*-{ this._d.onLoad(this) }-*/;

    final native JsArray<JavaScriptObject> unload() /*-{ return this._u }-*/;

    final native String getPluginName() /*-{ return this._d.pluginName; }-*/;

    final native void put(String k, String v) /*-{ this.p[k] = v; }-*/;

    final native void putInt(String k, int v) /*-{ this.p[k] = v; }-*/;

    final native void putBoolean(String k, boolean v) /*-{ this.p[k] = v; }-*/;

    final native void putObject(String k, JavaScriptObject v) /*-{ this.p[k] = v; }-*/;

    private static native Context create(JavaScriptObject T, Definition d, Element e)
        /*-{ return new T(d,e) }-*/ ;

    private static final JavaScriptObject TYPE = init();

    private static native JavaScriptObject init() /*-{
      var T = function(d,e) {
        this._d = d;
        this._u = [];
        this.body = e;
        this.p = {};
      };
      T.prototype = {
        onUnload: function(f){this._u.push(f)},
      };
      return T;
    }-*/;

    protected Context() {}
  }
}
