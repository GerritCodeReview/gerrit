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
  public static final JavaScriptObject TYPE = init();

  // Used from core and plugins
  private static native JavaScriptObject init() /*-{
    if ($wnd.Gerrit === undefined || $wnd.Gerrit.JsonString === undefined) {
      return function(s){this.s=s};
    } else {
      return $wnd.Gerrit.JsonString;
    }
  }-*/;

  static NativeString wrap(String s) {
    return wrap0(TYPE, s);
  }

  private static native NativeString wrap0(JavaScriptObject T, String s) /*-{ return new T(s) }-*/;

  public native String asString() /*-{ return this.s; }-*/;

  public static AsyncCallback<NativeString> unwrap(final AsyncCallback<String> cb) {
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

  public static boolean is(JavaScriptObject o) {
    return is(TYPE, o);
  }

  private static native boolean is(JavaScriptObject T, JavaScriptObject o)
      /*-{ return o instanceof T }-*/ ;

  protected NativeString() {}
}
