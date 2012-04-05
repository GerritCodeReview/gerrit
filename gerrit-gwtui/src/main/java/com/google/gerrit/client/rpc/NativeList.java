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

import java.util.AbstractList;
import java.util.List;

/** A read-only list of native JavaScript objects stored in a JSON array. */
public class NativeList<T extends JavaScriptObject> extends JavaScriptObject {
  protected NativeList() {
  }

  public final List<T> asList() {
    return new AbstractList<T>() {
      @Override
      public T set(int index, T element) {
        T old = NativeList.this.get(index);
        NativeList.this.set0(index, element);
        return old;
      }

      @Override
      public T get(int index) {
        return NativeList.this.get(index);
      }

      @Override
      public int size() {
        return NativeList.this.size();
      }
    };
  }

  public final boolean isEmpty() {
    return size() == 0;
  }

  public final native int size() /*-{ return this.length; }-*/;
  public final native T get(int i) /*-{ return this[i]; }-*/;
  private final native void set0(int i, T v) /*-{ this[i] = v; }-*/;
}
