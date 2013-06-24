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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for grouping together callbacks and calling them in order.
 * <p>
 * Callbacks are added to the group with {@link #add(AsyncCallback)}, which
 * returns a wrapped callback suitable for passing to an asynchronous RPC call.
 * The last callback must be added using {@link #addFinal(AsyncCallback)} or
 * {@link #done()} must be invoked.
 *
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
  private final List<CallbackImpl<?>> callbacks;
  private final Set<CallbackImpl<?>> remaining;
  private boolean finalAdded;

  private boolean failed;
  private Throwable failedThrowable;

  public static <T> Callback<T> emptyCallback() {
    return new Callback<T>() {
      @Override
      public void onSuccess(T result) {
      }

      @Override
      public void onFailure(Throwable err) {
      }
    };
  }

  public CallbackGroup() {
    callbacks = new ArrayList<CallbackImpl<?>>();
    remaining = new HashSet<CallbackImpl<?>>();
  }

  public <T> Callback<T> add(final AsyncCallback<T> cb) {
    checkFinalAdded();
    return handleAdd(cb);
  }

  public <T> Callback<T> addFinal(final AsyncCallback<T> cb) {
    checkFinalAdded();
    done();
    return handleAdd(cb);
  }

  public void done() {
    finalAdded = true;
  }

  private <T> Callback<T> handleAdd(AsyncCallback<T> cb) {
    if (failed) {
      cb.onFailure(failedThrowable);
      return emptyCallback();
    }

    CallbackImpl<T> wrapper = new CallbackImpl<T>(cb);
    callbacks.add(wrapper);
    remaining.add(wrapper);
    return wrapper;
  }

  private void checkFinalAdded() {
    if (finalAdded) {
      throw new IllegalStateException("final callback already added");
    }
  }

  private void onSuccess() {
    for (CallbackImpl<?> o : callbacks) {
      @SuppressWarnings("unchecked")
      CallbackImpl<Object> cb = (CallbackImpl<Object>) o.delegate;
      if (cb != null) {
        o.delegate = null;
        cb.onSuccess(cb.result);
        o.result = null;
      }
    }
    callbacks.clear();
  }

  private void onFailure(Throwable caught) {
    failed = true;
    failedThrowable = caught;
    for (CallbackImpl<?> cb : callbacks) {
      cb.delegate.onFailure(caught);
      cb.delegate = null;
      cb.result = null;
    }
    callbacks.clear();
    remaining.clear();
  }

  public interface Callback<T>
      extends AsyncCallback<T>, com.google.gwtjsonrpc.common.AsyncCallback<T> {
  }

  private class CallbackImpl<T> implements Callback<T> {
    AsyncCallback<T> delegate;
    T result;

    CallbackImpl(AsyncCallback<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onSuccess(T value) {
      if (!failed) {
        this.result = value;
        remaining.remove(this);
        if (finalAdded && remaining.isEmpty()) {
          CallbackGroup.this.onSuccess();
        }
      }
    }

    @Override
    public void onFailure(Throwable caught) {
      if (!failed) {
        CallbackGroup.this.onFailure(caught);
      }
    }
  }
}
