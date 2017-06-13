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

package com.google.gerrit.plugin.client.rpc;

import com.google.gerrit.client.rpc.NativeString;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class RestApi {
  private final StringBuilder path;
  private boolean hasQueryParams;

  public RestApi(String name) {
    path = new StringBuilder();
    path.append(name);
  }

  public RestApi view(String name) {
    return idRaw(name);
  }

  public RestApi view(String pluginName, String name) {
    return idRaw(pluginName + "~" + name);
  }

  public RestApi id(String id) {
    return idRaw(URL.encodePathSegment(id));
  }

  public RestApi id(int id) {
    return idRaw(Integer.toString(id));
  }

  public RestApi idRaw(String name) {
    if (hasQueryParams) {
      throw new IllegalStateException();
    }
    if (path.charAt(path.length() - 1) != '/') {
      path.append('/');
    }
    path.append(name);
    return this;
  }

  public RestApi addParameter(String name, String value) {
    return addParameterRaw(name, URL.encodeQueryString(value));
  }

  public RestApi addParameter(String name, String... value) {
    for (String val : value) {
      addParameter(name, val);
    }
    return this;
  }

  public RestApi addParameterTrue(String name) {
    return addParameterRaw(name, null);
  }

  public RestApi addParameter(String name, boolean value) {
    return addParameterRaw(name, value ? "t" : "f");
  }

  public RestApi addParameter(String name, int value) {
    return addParameterRaw(name, String.valueOf(value));
  }

  public RestApi addParameter(String name, Enum<?> value) {
    return addParameterRaw(name, value.name());
  }

  public RestApi addParameterRaw(String name, String value) {
    if (hasQueryParams) {
      path.append("&");
    } else {
      path.append("?");
      hasQueryParams = true;
    }
    path.append(name);
    if (value != null) {
      path.append("=").append(value);
    }
    return this;
  }

  public String path() {
    return path.toString();
  }

  public <T extends JavaScriptObject> void get(AsyncCallback<T> cb) {
    get(path(), wrap(cb));
  }

  public void getString(AsyncCallback<String> cb) {
    get(NativeString.unwrap(cb));
  }

  private static native void get(String p, JavaScriptObject r) /*-{ $wnd.Gerrit.get_raw(p, r) }-*/;

  public <T extends JavaScriptObject> void put(AsyncCallback<T> cb) {
    put(path(), wrap(cb));
  }

  private static native void put(String p, JavaScriptObject r) /*-{ $wnd.Gerrit.put_raw(p, r) }-*/;

  public <T extends JavaScriptObject> void put(String content, AsyncCallback<T> cb) {
    put(path(), content, wrap(cb));
  }

  private static native void put(String p, String c, JavaScriptObject r)
      /*-{ $wnd.Gerrit.put_raw(p, c, r) }-*/ ;

  public <T extends JavaScriptObject> void put(JavaScriptObject content, AsyncCallback<T> cb) {
    put(path(), content, wrap(cb));
  }

  private static native void put(String p, JavaScriptObject c, JavaScriptObject r)
      /*-{ $wnd.Gerrit.put_raw(p, c, r) }-*/ ;

  public <T extends JavaScriptObject> void post(String content, AsyncCallback<T> cb) {
    post(path(), content, wrap(cb));
  }

  private static native void post(String p, String c, JavaScriptObject r)
      /*-{ $wnd.Gerrit.post_raw(p, c, r) }-*/ ;

  public <T extends JavaScriptObject> void post(JavaScriptObject content, AsyncCallback<T> cb) {
    post(path(), content, wrap(cb));
  }

  private static native void post(String p, JavaScriptObject c, JavaScriptObject r)
      /*-{ $wnd.Gerrit.post_raw(p, c, r) }-*/ ;

  public void delete(AsyncCallback<NoContent> cb) {
    delete(path(), wrap(cb));
  }

  private static native void delete(String p, JavaScriptObject r)
      /*-{ $wnd.Gerrit.del_raw(p, r) }-*/ ;

  private static native <T extends JavaScriptObject> JavaScriptObject wrap(
      AsyncCallback<T> b) /*-{
    return function(r) {
      b.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(r)
    }
  }-*/;
}
