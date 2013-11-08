// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.rpc;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.json.client.JSONObject;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Natives {
  /**
   * Get the names of defined properties on the object. The returned set
   * iterates in the native iteration order, which may match the source order.
   */
  public static Set<String> keys(JavaScriptObject obj) {
    if (obj != null) {
      return new JSONObject(obj).keySet();
    }
    return Collections.emptySet();
  }

  public static <T extends JavaScriptObject> List<T> asList(
      final JsArray<T> arr) {
    if (arr == null) {
      return null;
    }
    return new AbstractList<T>() {
      @Override
      public T set(int index, T element) {
        T old = arr.get(index);
        arr.set(index, element);
        return old;
      }

      @Override
      public T get(int index) {
        return arr.get(index);
      }

      @Override
      public int size() {
        return arr.length();
      }
    };
  }

  public static <T extends JavaScriptObject> JsArray<T> arrayOf(T element) {
    JsArray<T> arr = JavaScriptObject.createArray().cast();
    arr.push(element);
    return arr;
  }

  @SuppressWarnings("unchecked")
  public static <T extends JavaScriptObject> T parseJSON(String json) {
    if (json.startsWith("\"")) {
      return (T) NativeString.wrap(parseString(parser, json));
    }
    return Natives.<T> parseObject(parser, json); // javac generics bug
  }

  private static native <T extends JavaScriptObject>
  T parseObject(JavaScriptObject p, String s)
  /*-{ return p(s); }-*/;

  private static native
  String parseString(JavaScriptObject p, String s)
  /*-{ return p(s); }-*/;

  private static JavaScriptObject parser;
  private static native JavaScriptObject bestJsonParser()
  /*-{
    if ($wnd.JSON && typeof $wnd.JSON.parse === 'function')
      return $wnd.JSON.parse;
    return function(s) { return eval('(' + s + ')'); };
  }-*/;

  static {
    parser = bestJsonParser();
  }

  private Natives() {
  }
}
