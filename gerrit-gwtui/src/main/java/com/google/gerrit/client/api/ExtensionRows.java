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
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ExtensionRows {
  private static final Logger logger =
      Logger.getLogger(ExtensionRows.class.getName());
  private final GerritUiExtensionPoint extensionPoint;
  private final List<Context> contexts;
  private Grid table;
  private Map<Definition, SimplePanel> panelsByDefinition;

  public ExtensionRows(GerritUiExtensionPoint extensionPoint, Grid table) {
    this.extensionPoint = extensionPoint;
    this.contexts = create();
    this.table = table;
  }

  private List<Context> create() {
    List<Context> contexts = new ArrayList<Context>();
    panelsByDefinition = new HashMap<Definition, SimplePanel>();
    for (Definition def : Natives.asList(Definition.get(extensionPoint.name()))) {
      SimplePanel p = new SimplePanel();
      panelsByDefinition.put(def, p);
      contexts.add(Context.create(def, p));
    }
    return contexts;
  }

  /** This class assumes an extensionRow consists of a plugin
   *  provided "text" header and a widget for the data column.
   *  This class makes no assumptions  about which columns
   *  the header or widget are in, so it uses the methods
   *  below to operate on them.
   */
  protected abstract String getHeader(int row);
  protected abstract void setHeader(int row, String header);
  protected abstract void setWidget(int row, Widget widget);

  public void putInt(GerritUiExtensionPoint.Key key, int value) {
    for (Context ctx : contexts) {
      ctx.putInt(key.name(), value);
    }
  }

  // Since inserting and removing rows changes all of the row indexes
  // below the inserted/removed row, we use a strategy for inserting
  // and removing rows that never changes the index of an inserted
  // extension row once it is in the table.  That means we can only
  // ever operate on rows that have a higher index than the highest
  // indexed extension row currently in the table.
  //
  // The strategies must then be:
  // * INSERT: insert from lowest to highest index
  // * REMOVE: remove from highest to lowest index

  public void insertRows() {
    for (Entry<Definition, SimplePanel> e : panelsByDefinition.entrySet()) {
      int row = table.getRowCount();
      table.insertRow(row);
      setHeader(row, e.getKey().getHeader());
      setWidget(row, e.getValue());
    }
    onLoad();
  }

  public void removeRows() {
    for (Entry<Definition, SimplePanel> e : panelsByDefinition.entrySet()) {
      table.removeRow(table.getRowCount() - 1);
    }
    onUnload();
  }

  private void onLoad() {
    for (Context ctx : contexts) {
      try {
        ctx.onLoad();
      } catch (RuntimeException e) {
        logger.log(Level.SEVERE,
            "Failed to load extension row for extension point "
                + extensionPoint.name() + " from plugin " + ctx.getPluginName()
                + ": " + e.getMessage());
      }
    }
  }

  private void onUnload() {
    for (Context ctx : contexts) {
      for (JavaScriptObject u : Natives.asList(ctx.unload())) {
        ApiGlue.invoke(u);
      }
    }
  }

  public static class Definition extends JavaScriptObject {
    static final JavaScriptObject TYPE = init();
    private static native JavaScriptObject init() /*-{
      function InfoRowDefinition(n, h, c) {
        this.pluginName = n;
        this.header = h;
        this.onLoad = c;
      };
      return InfoRowDefinition;
    }-*/;

    static native JsArray<Definition> get(String i)
    /*-{ return $wnd.Gerrit.infoRows[i] || [] }-*/;

    protected Definition() {
    }

    public final native String getPluginName() /*-{ return this.pluginName; }-*/;
    public final native String getHeader() /*-{ return this.header; }-*/;
  }

  static class Context extends JavaScriptObject {
    static final Context create(
        Definition def,
        SimplePanel panel) {
      return create(TYPE, def, panel.getElement());
    }

    final native void onLoad() /*-{ this._d.onLoad(this) }-*/;
    final native JsArray<JavaScriptObject> unload() /*-{ return this._u }-*/;
    final native String getPluginName() /*-{ return this._d.pluginName; }-*/;

    final native void putInt(String k, int v) /*-{ this.p[k] = v; }-*/;

    private static final native Context create(
        JavaScriptObject T,
        Definition d,
        Element e)
    /*-{ return new T(d,e) }-*/;

    private static final JavaScriptObject TYPE = init();
    private static final native JavaScriptObject init() /*-{
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

    protected Context() {
    }
  }
}
