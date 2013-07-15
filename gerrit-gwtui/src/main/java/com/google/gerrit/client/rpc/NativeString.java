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
import com.google.gwt.user.client.rpc.AsyncCallback;

/** Wraps a String that was returned from a JSON API. */
public final class NativeString extends JavaScriptObject {
  static final native NativeString wrap(String s) /*-{
    return new $wnd._NS(s);
  }-*/;

  public final native String asString() /*-{ return this.s; }-*/;
  private final native void set(String v) /*-{ this.s = v; }-*/;

  public static final AsyncCallback<NativeString>
  unwrap(final AsyncCallback<String> cb) {
    return new AsyncCallback<NativeString>() {
      @Override
      public void onSuccess(NativeString result) {
        cb.onSuccess(result != null ? result.asString() : null);
      }

      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }
    };
  }

  static {
    init();
  }

  private static final native void init() /*-{
    $wnd._NS = function(s){this.s=s}
  }-*/;

  public static final native boolean is(JavaScriptObject o) /*-{
    return o instanceof $wnd._NS;
  }-*/;

  protected NativeString() {
  }
}
