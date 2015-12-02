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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ExtensionRows {
  private static final Logger logger =
      Logger.getLogger(ExtensionRows.class.getName());

  private final GerritUiExtensionPoint extensionPoint;
  private Grid table;
  private Map<Definition, Row> rowsByDefinition;
  private List<Row> orderedRows;

  public ExtensionRows(GerritUiExtensionPoint extensionPoint, Grid table) {
    this.extensionPoint = extensionPoint;
    this.table = table;
    init();
  }

  private void init() {
    rowsByDefinition = new HashMap<Definition, Row>();
    for (Definition def : Natives.asList(Definition.get(extensionPoint.name()))) {
      SimplePanel p = new SimplePanel();
      Context c = Context.create(def, p);
      rowsByDefinition.put(def, new Row(def, p, c, -1));
    }
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
    for (Row r : rowsByDefinition.values()) {
      Context ctx = r.getContext();
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
    removeRows();
    orderRows();
    for (Row r : orderedRows) {
      int row = r.getRow();
      table.insertRow(row);
      setHeader(row, r.getHeader());
      setWidget(row, r.getWidget());
    }
    onLoad();
  }

  public void removeRows() {
    if (orderedRows != null) {
      Collections.reverse(orderedRows);
      for (Row r : orderedRows) {
        table.removeRow(r.getRow());
      }
      onUnload();
      orderedRows = null;
    }
  }

  private void onLoad() {
    for (Row r : rowsByDefinition.values()) {
      Context ctx = r.getContext();
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
    for (Row r : rowsByDefinition.values()) {
      for (JavaScriptObject u : Natives.asList(r.getContext().unload())) {
        ApiGlue.invoke(u);
      }
    }
  }

  /** Order the rows and calculate their intended final row index */
  private void orderRows() {
    orderedRows = new ArrayList<Row>();

    Map<String, Row> rowsByAfters = getRowsByAfters();
    for (int row = 0; row < table.getRowCount(); row++) {
      String header = table.getText(row, 0);
      Row r = rowsByAfters.remove(header);
      while (r != null) {
        r.setRow(row + 1 + orderedRows.size());
        orderedRows.add(r);

        // Does anything point to the newly added extension?
        r = rowsByAfters.remove(r.getHeader());
      }
    }
  }

  /**
    * Order all the rows (core and extension) in an "after" map that can
    * be traversed in order.
    */
  private Map<String, Row> getRowsByAfters() {
    Map<String, Row> rowsByAfters = new HashMap<String, Row>();
    Set<String> headers = getHeaderSet();
    for (Row r : rowsByDefinition.values()) {
      String header = r.getHeader();
      headers.add(header);

      Row previous = rowsByAfters.put(r.getAfter(), r);
      if (previous != null) {
        // Repoint the duplicate to the latest so both get added
        rowsByAfters.put(header, previous);
      }
    }

    String lastHeader = table.getText(table.getRowCount() -1, 0);
    repointMissing(rowsByAfters, headers, lastHeader);

    return rowsByAfters;
  }

  /** For any row pointing to a missing row, repoint in to "header" */
  private void repointMissing(Map<String, Row> rowsByAfters,
      Set<String> headers, String header) {
    for (String after : new HashSet<String>(rowsByAfters.keySet())) {
      if (!headers.contains(after)) {
        Row r = rowsByAfters.remove(after);
        Row previous = rowsByAfters.put(header, r);
        if (previous != null) {
          // Repoint the duplicate to the latest so both get added
          rowsByAfters.put(r.getHeader(), previous);
        }
      }
    }
  }

  /** Gets the Set of core headers */
  private Set<String> getHeaderSet() {
    Set<String> headers = new HashSet<String>();
    for (int row = 0; row < table.getRowCount(); row++) {
      headers.add(getHeader(row));
    }
    return headers;
  }

  public static class Definition extends JavaScriptObject {
    static final JavaScriptObject TYPE = init();
    private static native JavaScriptObject init() /*-{
      function InfoRowDefinition(n, h, a, c) {
        this.pluginName = n;
        this.header = h;
        this.after = a;
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
    public final native String getAfter() /*-{ return this.after; }-*/;
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

  static class Row {
    private Definition definition;
    private SimplePanel panel;
    private Context context;
    private int row;

    public Row(Definition def, SimplePanel p, Context c, int row) {
      this.definition = def;
      this.panel = p;
      this.context = c;
      this.row = row;
    }

    public Context getContext() {
      return context;
    }

    public String getHeader() {
      return definition.getHeader();
    }

    public String getAfter() {
      return definition.getAfter();
    }

    public Widget getWidget() {
      return panel;
    }

    public int getRow() {
      return row;
    }

    public void setRow(int r) {
      row = r;
    }
  }
}
