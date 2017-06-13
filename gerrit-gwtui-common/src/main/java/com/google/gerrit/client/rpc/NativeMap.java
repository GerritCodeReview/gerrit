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
import com.google.gwt.user.client.rpc.AsyncCallback;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** A map of native JSON objects, keyed by a string. */
public class NativeMap<T extends JavaScriptObject> extends JavaScriptObject {
  public static <T extends JavaScriptObject> NativeMap<T> create() {
    return createObject().cast();
  }

  /**
   * Loop through the result map's entries and copy the key strings into the "name" property of the
   * corresponding child object. This only runs on the top level map of the result, and requires the
   * children to be JSON objects and not a JSON primitive (e.g. boolean or string).
   */
  public static <T extends JavaScriptObject, M extends NativeMap<T>>
      AsyncCallback<M> copyKeysIntoChildren(AsyncCallback<M> callback) {
    return copyKeysIntoChildren("name", callback);
  }

  /** Loop through the result map and set asProperty on the children. */
  public static <T extends JavaScriptObject, M extends NativeMap<T>>
      AsyncCallback<M> copyKeysIntoChildren(String asProperty, AsyncCallback<M> callback) {
    return new TransformCallback<M, M>(callback) {
      @Override
      protected M transform(M result) {
        result.copyKeysIntoChildren(asProperty);
        return result;
      }
    };
  }

  protected NativeMap() {}

  public final Set<String> keySet() {
    return Natives.keys(this);
  }

  public final List<String> sortedKeys() {
    Set<String> keys = keySet();
    List<String> sorted = new ArrayList<>(keys);
    Collections.sort(sorted);
    return sorted;
  }

  public final native JsArray<T> values() /*-{
    var s = this;
    var v = [];
    var i = 0;
    for (var k in s) {
      if (s.hasOwnProperty(k)) {
        v[i++] = s[k];
      }
    }
    return v;
  }-*/;

  public final int size() {
    return keySet().size();
  }

  public final boolean isEmpty() {
    return size() == 0;
  }

  public final boolean containsKey(String n) {
    return get(n) != null;
  }

  public final native T get(String n) /*-{ return this[n]; }-*/;

  public final native void put(String n, T v) /*-{ this[n] = v; }-*/;

  public final native void copyKeysIntoChildren(String p) /*-{
    var s = this;
    for (var k in s) {
      if (s.hasOwnProperty(k)) {
        var c = s[k];
        c[p] = k;
      }
    }
  }-*/;
}
