package com.google.gerrit.client.api;

import com.google.gerrit.client.changes.ChangeInfo;
import com.google.gerrit.client.changes.ChangeInfo.ActionInfo;
import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;

class ActionContext extends JavaScriptObject {
  static final native ActionContext create(RestApi f)/*-{
    return new $wnd.Gerrit.RpcContext(f);
  }-*/;

  final native void set(ActionInfo a) /*-{ this.action=a; }-*/;
  final native void set(ChangeInfo c) /*-{ this.change=c; }-*/;
  final native void set(RevisionInfo r) /*-{ this.revision=r; }-*/;

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
