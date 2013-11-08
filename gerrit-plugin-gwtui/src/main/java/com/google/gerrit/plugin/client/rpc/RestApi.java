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

  public RestApi id(String id) {
    return idRaw(URL.encodeQueryString(id));
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

  public void get(AsyncCallback<JavaScriptObject> cb) {
    get(path(), cb);
  }

  private native static void get(String path,
      AsyncCallback<JavaScriptObject> cb) /*-{
    $wnd.Gerrit.get(path, function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;

  public void put(AsyncCallback<JavaScriptObject> cb) {
    put(path(), cb);
  }

  private native static void put(String path,
      AsyncCallback<JavaScriptObject> cb) /*-{
    $wnd.Gerrit.put(path, function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;

  public void put(JavaScriptObject content, AsyncCallback<JavaScriptObject> cb) {
    put(path(), content, cb);
  }

  private native static void put(String path,
      JavaScriptObject content, AsyncCallback<JavaScriptObject> cb) /*-{
    $wnd.Gerrit.put(path, content, function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;

  public void post(JavaScriptObject content, AsyncCallback<JavaScriptObject> cb) {
    post(path(), content, cb);
  }

  private native static void post(String path,
      JavaScriptObject content, AsyncCallback<JavaScriptObject> cb) /*-{
    $wnd.Gerrit.post(path, content, function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;

  public void post(String content, AsyncCallback<JavaScriptObject> cb) {
    post(path(), content, cb);
  }

  private native static void post(String path, String content,
      AsyncCallback<JavaScriptObject> cb) /*-{
    $wnd.Gerrit.post(path, $wnd.Gerrit.wrapString(content), function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;

  public void delete(AsyncCallback<JavaScriptObject> cb) {
    delete(path(), cb);
  }

  private native static void delete(String path,
      AsyncCallback<JavaScriptObject> cb) /*-{
    '$wnd.Gerrit.delete'(path, function(result) {
      cb.@com.google.gwt.user.client.rpc.AsyncCallback::onSuccess(Ljava/lang/Object;)(result);
    });
  }-*/;
}
