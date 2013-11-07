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

import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.NativeString;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gwt.core.client.JavaScriptObject;

public class JavaScriptRestApi {

  public static void init() {
    initRestCallJsBridge();
  }

  private static native void initRestCallJsBridge() /*-{
    $wnd['do_rest_get'] = function(path, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::get(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(path, cb);
    };
    $wnd['do_rest_put'] = function(path, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::put(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(path, cb);
    };
    $wnd['do_rest_put_content'] = function(path, content, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::put(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(path, content, cb);
    };
    $wnd['do_rest_post'] = function(path, content, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::post(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;Lcom/google/gwt/core/client/JavaScriptObject;)(path, content, cb);
    };
    $wnd['do_rest_post_string_content'] = function(path, content, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::post(Ljava/lang/String;Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(path, content, cb);
    };
    $wnd['do_rest_delete'] = function(path, cb) {
      @com.google.gerrit.client.api.JavaScriptRestApi::delete(Ljava/lang/String;Lcom/google/gwt/core/client/JavaScriptObject;)(path, cb);
    };
  }-*/;

  private static <T extends JavaScriptObject> void get(String path,
      JavaScriptObject cb) {
    new RestApi(path).get(wrap(cb));
  }

  private static <T extends JavaScriptObject> void put(String path,
      JavaScriptObject cb) {
    new RestApi(path).put(wrap(cb));
  }

  private static <T extends JavaScriptObject> void put(String path,
      JavaScriptObject content, JavaScriptObject cb) {
    new RestApi(path).put(content, wrap(cb));
  }

  private static <T extends JavaScriptObject> void post(String path,
      JavaScriptObject content, JavaScriptObject cb) {
    new RestApi(path).post(content, wrap(cb));
  }

  private static <T extends JavaScriptObject> void post(String path,
      String content, JavaScriptObject cb) {
    new RestApi(path).post(content, wrap(cb));
  }

  private static <T extends JavaScriptObject> void delete(String path,
      JavaScriptObject cb) {
    new RestApi(path).get(wrap(cb));
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
