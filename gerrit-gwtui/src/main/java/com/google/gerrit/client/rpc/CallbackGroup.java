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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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
  private final Set<CallbackImpl<?>> callbacks;
  private final Set<CallbackImpl<?>> remaining;
  private Throwable failedThrowable;
  private boolean failed;

  public static <T> AsyncCallback<T> emptyCallback() {
    return newCallback();
  }

  private static <T> Callback<T> newCallback() {
    return new Callback<T>() {
      @Override
      public void onFailure(Throwable arg0) {
      }

      @Override
      public void onSuccess(T arg0) {
      }
    };
  }

  private interface Callback<T> extends AsyncCallback<T>, com.google.gwtjsonrpc.common.AsyncCallback<T> {
  }

  private class CallbackImpl<T> implements Callback<T> {
    private Callback<T> delegate;
    private T result;

    private CallbackImpl(Callback<T> delegate) {
      this.delegate = delegate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onSuccess(T value) {
      result = value;
      remaining.remove(this);

      if (!remaining.isEmpty()) {
        return;
      }
      for (CallbackImpl<?> cb : callbacks) {
        ((CallbackImpl<Object>)cb).delegate.onSuccess(cb.result);
        cb.delegate = null;
      }
      callbacks.clear();
    }

    @Override
    public void onFailure(Throwable caught) {
      if (failed) {
        return;
      }
      failed = true;
      failedThrowable = caught;
      for (CallbackImpl<?> cb : callbacks) {
        cb.delegate.onFailure(caught);
        cb.delegate = null;
      }
      callbacks.clear();
      remaining.clear();
    }
  }

  public CallbackGroup() {
    callbacks = new LinkedHashSet<CallbackImpl<?>>();
    remaining = new HashSet<CallbackImpl<?>>();
  }

  public <T> AsyncCallback<T> add(final AsyncCallback<T> cb) {
    return handleAdd(new Callback<T>() {
      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }

      @Override
      public void onSuccess(T value) {
        cb.onSuccess(value);
      }
    });
  }

  public <T> com.google.gwtjsonrpc.common.AsyncCallback<T> addGwtjsonrpc(
      final com.google.gwtjsonrpc.common.AsyncCallback<T> cb) {
    return handleAdd(new Callback<T>() {
      @Override
      public void onFailure(Throwable caught) {
        cb.onFailure(caught);
      }

      @Override
      public void onSuccess(T value) {
        cb.onSuccess(value);
      }
    });
  }

  private <T> Callback<T> handleAdd(Callback<T> delegate) {
    if (failed) {
      delegate.onFailure(failedThrowable);
      return newCallback();
    }

    CallbackImpl<T> wrapper = new CallbackImpl<T>(delegate);
    callbacks.add(wrapper);
    remaining.add(wrapper);
    return wrapper;
  }
}
