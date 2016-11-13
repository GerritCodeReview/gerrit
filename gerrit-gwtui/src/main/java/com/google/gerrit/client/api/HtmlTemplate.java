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
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.StyleInjector;
import com.google.gwt.user.client.DOM;
import com.google.gwtexpui.safehtml.client.SafeHtmlBuilder;

final class HtmlTemplate {
  static native void init() /*-{
    var ElementSet = function(r,e) {
      this.root = r;
      this.elements = e;
    };
    ElementSet.prototype = {
      clear: function() {
        this.root = null;
        this.elements = null;
      },
    };

    $wnd.Gerrit.css = @com.google.gerrit.client.api.HtmlTemplate::css(Ljava/lang/String;);
    $wnd.Gerrit.html = function(h,r,w) {
      var i = {};
      if (r) {
        h = h.replace(
          /\sid=['"]\{([a-z_][a-z0-9_]*)\}['"]|\{([a-z0-9._-]+)\}/gi,
          function(m,a,b) {
            if (a)
              return @com.google.gerrit.client.api.HtmlTemplate::id(
                  Lcom/google/gerrit/client/api/HtmlTemplate$IdMap;
                  Ljava/lang/String;)
                (i,a);
            return @com.google.gerrit.client.api.HtmlTemplate::html(
                Lcom/google/gerrit/client/api/HtmlTemplate$ReplacementMap;
                Ljava/lang/String;)
              (r,b);
          });
      }
      var e = @com.google.gerrit.client.api.HtmlTemplate::parseHtml(
          Ljava/lang/String;Lcom/google/gerrit/client/api/HtmlTemplate$IdMap;
          Lcom/google/gerrit/client/api/HtmlTemplate$ReplacementMap;
          Z)
        (h,i,r,!!w);
      return w ? new ElementSet(e,i) : e;
    };
  }-*/;

  private static String css(String css) {
    String name = DOM.createUniqueId();
    StyleInjector.inject("." + name + "{" + css + "}");
    return name;
  }

  private static String id(IdMap idMap, String key) {
    String id = DOM.createUniqueId();
    idMap.put(id, key);
    return " id='" + id + "'";
  }

  private static String html(ReplacementMap opts, String id) {
    int d = id.indexOf('.');
    if (0 < d) {
      String name = id.substring(0, d);
      String rest = id.substring(d + 1);
      return html(opts.map(name), rest);
    }
    return new SafeHtmlBuilder().append(opts.str(id)).asString();
  }

  private static Node parseHtml(String html, IdMap ids, ReplacementMap opts, boolean wantElements) {
    Element div = Document.get().createDivElement();
    div.setInnerHTML(html);
    if (!ids.isEmpty()) {
      attachHandlers(div, ids, opts, wantElements);
    }
    if (div.getChildCount() == 1) {
      return div.getFirstChild();
    }
    return div;
  }

  private static void attachHandlers(
      Element e, IdMap ids, ReplacementMap opts, boolean wantElements) {
    if (e.getId() != null) {
      String key = ids.get(e.getId());
      if (key != null) {
        ids.remove(e.getId());
        if (wantElements) {
          ids.put(key, e);
        }
        e.setId(null);
        opts.map(key).attachHandlers(e);
      }
    }
    for (Element c = e.getFirstChildElement(); c != null; ) {
      attachHandlers(c, ids, opts, wantElements);
      c = c.getNextSiblingElement();
    }
  }

  private static class ReplacementMap extends JavaScriptObject {
    final native ReplacementMap map(String n) /*-{ return this[n] }-*/;

    final native String str(String n) /*-{ return ''+this[n] }-*/;

    final native void attachHandlers(Element e) /*-{
      for (var k in this) {
        var f = this[k];
        if (k.substring(0, 2) == 'on' && typeof f == 'function')
          e[k] = f;
      }
    }-*/;

    protected ReplacementMap() {}
  }

  private static class IdMap extends JavaScriptObject {
    final native String get(String i) /*-{ return this[i] }-*/;

    final native void remove(String i) /*-{ delete this[i] }-*/;

    final native void put(String i, String k) /*-{ this[i] = k }-*/;

    final native void put(String k, Element e) /*-{ this[k] = e }-*/;

    final native boolean isEmpty() /*-{
      for (var i in this)
        return false;
      return true;
    }-*/;

    protected IdMap() {}
  }

  private HtmlTemplate() {}
}
