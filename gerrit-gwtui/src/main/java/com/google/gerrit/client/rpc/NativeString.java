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
import com.google.gwtjsonrpc.common.AsyncCallback;

/** Wraps a String that was returned from a JSON API. */
public final class NativeString extends JavaScriptObject {
  public final native String asString() /*-{ return this; }-*/;

  public static final AsyncCallback<NativeString>
  unwrap(final AsyncCallback<String> cb) {
    return new AsyncCallback<NativeString>() {
      @Override
      public void onSuccess(NativeString result) {
        cb.onSuccess(result.asString());
      }

      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }
    };
  }

  protected NativeString() {
  }
}
