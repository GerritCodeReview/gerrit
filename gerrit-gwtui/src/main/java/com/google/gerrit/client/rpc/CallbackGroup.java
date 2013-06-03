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

package com.google.gerrit.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for grouping together callbacks and calling them in order.
 * <p>
 * Callbacks are added to the group with {@link #add(AsyncCallback)}, which
 * returns a wrapped callback suitable for passing to an asynchronous RPC call.
 * The enclosing group buffers returned results and ensures that
 * {@code onSuccess} is called exactly once for each callback in the group, in
 * the same order that callbacks were added. This allows callers to, for
 * example, use a {@link ScreenLoadCallback} as the last callback in the list
 * and only display the screen once all callbacks have succeeded.
 * <p>
 * In the event of a failure, the <em>first</em> caught exception is sent to
 * <em>all</em> callbacks' {@code onFailure} methods, in order; subsequent
 * successes or failures are all ignored. Note that this means
 * {@code onFailure} may be called with an exception unrelated to the callback
 * processing it.
 */
public class CallbackGroup {
  private final List<Object> callbacks;
  private final Map<Object, Object> results;
  private boolean failed;

  public static <T> AsyncCallback<T> emptyCallback() {
    return new AsyncCallback<T>() {
      @Override
      public void onSuccess(T result) {
      }

      @Override
      public void onFailure(Throwable err) {
      }
    };
  }

  public CallbackGroup() {
    callbacks = new ArrayList<Object>();
    results = new HashMap<Object, Object>();
  }

  public <T> AsyncCallback<T> add(final AsyncCallback<T> cb) {
    callbacks.add(cb);
    return new AsyncCallback<T>() {
      @Override
      public void onSuccess(T result) {
        results.put(cb, result);
        CallbackGroup.this.onSuccess();
      }

      @Override
      public void onFailure(Throwable caught) {
        CallbackGroup.this.onFailure(caught);
      }
    };
  }

  public <T> com.google.gwtjsonrpc.common.AsyncCallback<T> addGwtjsonrpc(
      final com.google.gwtjsonrpc.common.AsyncCallback<T> cb) {
    callbacks.add(cb);
    return new com.google.gwtjsonrpc.common.AsyncCallback<T>() {
      @Override
      public void onSuccess(T result) {
        results.put(cb, result);
        CallbackGroup.this.onSuccess();
      }

      @Override
      public void onFailure(Throwable caught) {
        CallbackGroup.this.onFailure(caught);
      }
    };
  }

  private void onSuccess() {
    if (results.size() < callbacks.size()) {
      return;
    }
    for (Object o : callbacks) {
      Object result = results.get(o);
      if (o instanceof AsyncCallback) {
        @SuppressWarnings("unchecked")
        AsyncCallback<Object> cb = (AsyncCallback<Object>) o;
        cb.onSuccess(result);
      } else {
        @SuppressWarnings("unchecked")
        com.google.gwtjsonrpc.common.AsyncCallback<Object> cb =
            (com.google.gwtjsonrpc.common.AsyncCallback<Object>) o;
        cb.onSuccess(result);
      }
    }
  }

  private void onFailure(Throwable caught) {
    if (failed) {
      return;
    }
    failed = true;
    for (Object o : callbacks) {
      if (o instanceof AsyncCallback) {
        @SuppressWarnings("unchecked")
        AsyncCallback<Object> cb = (AsyncCallback<Object>) o;
        cb.onFailure(caught);
      } else {
        @SuppressWarnings("unchecked")
        com.google.gwtjsonrpc.common.AsyncCallback<Object> cb =
            (com.google.gwtjsonrpc.common.AsyncCallback<Object>) o;
        cb.onFailure(caught);
      }
    }
  }
}
