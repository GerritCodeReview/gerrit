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

import com.google.gerrit.client.actions.ActionButton;
import com.google.gerrit.client.info.ActionInfo;
import com.google.gerrit.client.info.ChangeInfo;
import com.google.gerrit.client.info.ChangeInfo.EditInfo;
import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.projects.BranchInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;

public class ActionContext extends JavaScriptObject {
  static final native void init() /*-{
    var Gerrit = $wnd.Gerrit;
    var doc = $wnd.document;
    var stopPropagation = function (e) {
      if (e && e.stopPropagation) e.stopPropagation();
      else $wnd.event.cancelBubble = true;
    };

    Gerrit.ActionContext = function(u){this._u=u};
    Gerrit.ActionContext.prototype = {
      go: Gerrit.go,
      refresh: Gerrit.refresh,
      refreshMenuBar: Gerrit.refreshMenuBar,
      isSignedIn: Gerrit.isSignedIn,
      showError: Gerrit.showError,

      br: function(){return doc.createElement('br')},
      hr: function(){return doc.createElement('hr')},
      button: function(label, o) {
        var e = doc.createElement('button');
        e.appendChild(this.div(doc.createTextNode(label)));
        if (o && o.onclick) e.onclick = o.onclick;
        return e;
      },
      checkbox: function() {
        var e = doc.createElement('input');
        e.type = 'checkbox';
        return e;
      },
      div: function() {
        var e = doc.createElement('div');
        for (var i = 0; i < arguments.length; i++)
          e.appendChild(arguments[i]);
        return e;
      },
      label: function(c,label) {
        var e = doc.createElement('label');
        e.appendChild(c);
        e.appendChild(doc.createTextNode(label));
        return e;
      },
      prependLabel: function(label,c) {
        var e = doc.createElement('label');
        e.appendChild(doc.createTextNode(label));
        e.appendChild(c);
        return e;
      },
      span: function() {
        var e = doc.createElement('span');
        for (var i = 0; i < arguments.length; i++)
          e.appendChild(arguments[i]);
        return e;
      },
      msg: function(label) {
        var e = doc.createElement('span');
        e.appendChild(doc.createTextNode(label));
        return e;
      },
      textarea: function(o) {
        var e = doc.createElement('textarea');
        e.onkeypress = stopPropagation;
        if (o && o.rows) e.rows = o.rows;
        if (o && o.cols) e.cols = o.cols;
        return e;
      },
      textfield: function() {
        var e = doc.createElement('input');
        e.type = 'text';
        e.onkeypress = stopPropagation;
        return e;
      },
      select: function(a,s) {
        var e = doc.createElement('select');
        for (var i = 0; i < a.length; i++) {
          var o = doc.createElement('option');
          if (i==s) {
            o.setAttributeNode(doc.createAttribute("selected"));
          }
          o.appendChild(doc.createTextNode(a[i]));
          e.appendChild(o);
        }
        return e;
      },
      selected: function(e) {
        return e.options[e.selectedIndex].text;
      },

      popup: function(e){
        this._p=@com.google.gerrit.client.api.PopupHelper::popup(
          Lcom/google/gerrit/client/api/ActionContext;Lcom/google/gwt/dom/client/Element;)(this,e)},
      hide: function() {
        this._p.@com.google.gerrit.client.api.PopupHelper::hide()();
        delete this['_p'];
      },

      call: function(i,b) {
        var m = this.action.method.toLowerCase();
        if (m == 'get' || m == 'delete' || i==null) this[m](b);
        else this[m](i,b);
      },
      get: function(b){@com.google.gerrit.client.api.ActionContext::get(
        Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,b)},
      post: function(i,b){@com.google.gerrit.client.api.ActionContext::post(
        Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(
        this._u,i,b)},
      put: function(i,b){@com.google.gerrit.client.api.ActionContext::put(
        Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(
        this._u,i,b)},
      'delete': function(b){@com.google.gerrit.client.api.ActionContext::delete(
        Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,b)},
      del: function(b){@com.google.gerrit.client.api.ActionContext::delete(
        Lcom/google/gerrit/client/rpc/RestApi;Lcom/google/gwt/core/client/JavaScriptObject;)(this._u,b)},
    };
  }-*/;

  static final native ActionContext create(RestApi f) /*-{
    return new $wnd.Gerrit.ActionContext(f);
  }-*/;

  final native void set(ActionInfo a) /*-{ this.action=a; }-*/;

  final native void set(ChangeInfo c) /*-{ this.change=c; }-*/;

  final native void set(EditInfo e) /*-{ this.edit=e; }-*/;

  final native void set(Project.NameKey p) /*-{ this.project=p; }-*/;

  final native void set(BranchInfo b) /*-{ this.branch=b }-*/;

  final native void set(RevisionInfo r) /*-{ this.revision=r; }-*/;

  final native void button(ActionButton b) /*-{ this._b=b; }-*/;

  final native ActionButton button() /*-{ return this._b; }-*/;

  public final native boolean has_popup() /*-{ return this.hasOwnProperty('_p') }-*/;

  public final native void hide() /*-{ this.hide(); }-*/;

  protected ActionContext() {}

  static final void get(RestApi api, JavaScriptObject cb) {
    api.get(wrap(cb));
  }

  /**
   * The same as {@link #get(RestApi, JavaScriptObject)} but without converting a {@link
   * NativeString} result to String.
   */
  static final void getRaw(RestApi api, JavaScriptObject cb) {
    api.get(wrapRaw(cb));
  }

  static final void post(RestApi api, JavaScriptObject in, JavaScriptObject cb) {
    if (NativeString.is(in)) {
      post(api, ((NativeString) in).asString(), cb);
    } else {
      api.post(in, wrap(cb));
    }
  }

  /**
   * The same as {@link #post(RestApi, JavaScriptObject, JavaScriptObject)} but without converting a
   * {@link NativeString} result to String.
   */
  static final void postRaw(RestApi api, JavaScriptObject in, JavaScriptObject cb) {
    if (NativeString.is(in)) {
      postRaw(api, ((NativeString) in).asString(), cb);
    } else {
      api.post(in, wrapRaw(cb));
    }
  }

  static final void post(RestApi api, String in, JavaScriptObject cb) {
    api.post(in, wrap(cb));
  }

  /**
   * The same as {@link #post(RestApi, String, JavaScriptObject)} but without converting a {@link
   * NativeString} result to String.
   */
  static final void postRaw(RestApi api, String in, JavaScriptObject cb) {
    api.post(in, wrapRaw(cb));
  }

  static final void put(RestApi api, JavaScriptObject cb) {
    api.put(wrap(cb));
  }

  /**
   * The same as {@link #put(RestApi, JavaScriptObject)} but without converting a {@link
   * NativeString} result to String.
   */
  static final void putRaw(RestApi api, JavaScriptObject cb) {
    api.put(wrapRaw(cb));
  }

  static final void put(RestApi api, JavaScriptObject in, JavaScriptObject cb) {
    if (NativeString.is(in)) {
      put(api, ((NativeString) in).asString(), cb);
    } else {
      api.put(in, wrap(cb));
    }
  }

  /**
   * The same as {@link #put(RestApi, JavaScriptObject, JavaScriptObject)} but without converting a
   * {@link NativeString} result to String.
   */
  static final void putRaw(RestApi api, JavaScriptObject in, JavaScriptObject cb) {
    if (NativeString.is(in)) {
      putRaw(api, ((NativeString) in).asString(), cb);
    } else {
      api.put(in, wrapRaw(cb));
    }
  }

  static final void put(RestApi api, String in, JavaScriptObject cb) {
    api.put(in, wrap(cb));
  }

  /**
   * The same as {@link #put(RestApi, String, JavaScriptObject)} but without converting a {@link
   * NativeString} result to String.
   */
  static final void putRaw(RestApi api, String in, JavaScriptObject cb) {
    api.put(in, wrapRaw(cb));
  }

  static final void delete(RestApi api, JavaScriptObject cb) {
    api.delete(wrap(cb));
  }

  /**
   * The same as {@link #delete(RestApi, JavaScriptObject)} but without converting a {@link
   * NativeString} result to String.
   */
  static final void deleteRaw(RestApi api, JavaScriptObject cb) {
    api.delete(wrapRaw(cb));
  }

  private static GerritCallback<JavaScriptObject> wrap(JavaScriptObject cb) {
    return new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject result) {
        if (NativeString.is(result)) {
          NativeString s = result.cast();
          ApiGlue.invoke(cb, s.asString());
        } else {
          ApiGlue.invoke(cb, result);
        }
      }
    };
  }

  private static GerritCallback<JavaScriptObject> wrapRaw(JavaScriptObject cb) {
    return new GerritCallback<JavaScriptObject>() {
      @Override
      public void onSuccess(JavaScriptObject result) {
        ApiGlue.invoke(cb, result);
      }
    };
  }
}
