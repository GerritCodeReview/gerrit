package com.google.gerrit.client.api;

import com.google.gerrit.client.change.ActionButton;
import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;

public class ActionContext extends JavaScriptObject {
  static final native void init() /*-{
    $wnd.Gerrit.ActionContext = function(u){this._u=u};
    $wnd.Gerrit.ActionContext.prototype = {
      popup: function(e){this._p=@com.google.gerrit.client.api.PopupHelper::popup(Lcom/google/gerrit/client/api/ActionContext;Lcom/google/gwt/dom/client/Element;)(this,e)},
      hide: function() {
        this._p.@com.google.gerrit.client.api.PopupHelper::hide()();
        delete this['_p'];
      },
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
  }-*/;

  static final native ActionContext create(RestApi f)/*-{
    return new $wnd.Gerrit.ActionContext(f);
  }-*/;

  final native void set(ActionInfo a) /*-{ this.action=a; }-*/;
  final native void set(ChangeInfo c) /*-{ this.change=c; }-*/;
  final native void set(RevisionInfo r) /*-{ this.revision=r; }-*/;

  final native void button(ActionButton b) /*-{ this._b=b; }-*/;
  final native ActionButton button() /*-{ return this._b; }-*/;

  public final native boolean has_popup() /*-{ return this.hasOwnProperty('_p') }-*/;
  public final native void hide() /*-{ this.hide(); }-*/;

  protected ActionContext() {
  }

  private static final void get(RestApi api, final JavaScriptObject cb) {
    api.get(wrap(cb));
  }

  private static final void post(
      RestApi api, JavaScriptObject in, final JavaScriptObject cb) {
    api.post(in, wrap(cb));
  }

  private static final void put(
      RestApi api, JavaScriptObject in, final JavaScriptObject cb) {
    api.put(in, wrap(cb));
  }

  private static final void delete(RestApi api, final JavaScriptObject cb) {
    api.delete(wrap(cb));
  }

  private static GerritCallback<JavaScriptObject> wrap(final JavaScriptObject cb) {
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
}
