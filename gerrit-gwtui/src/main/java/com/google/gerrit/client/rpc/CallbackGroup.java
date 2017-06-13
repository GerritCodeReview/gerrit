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
 *
 * <p>Callbacks are added to the group with {@link #add(AsyncCallback)}, which returns a wrapped
 * callback suitable for passing to an asynchronous RPC call. The last callback must be added using
 * {@link #addFinal(AsyncCallback)} or {@link #done()} must be invoked.
 *
 * <p>The enclosing group buffers returned results and ensures that {@code onSuccess} is called
 * exactly once for each callback in the group, in the same order that callbacks were added. This
 * allows callers to, for example, use a {@link ScreenLoadCallback} as the last callback in the list
 * and only display the screen once all callbacks have succeeded.
 *
 * <p>In the event of a failure, the <em>first</em> caught exception is sent to <em>all</em>
 * callbacks' {@code onFailure} methods, in order; subsequent successes or failures are all ignored.
 * Note that this means {@code onFailure} may be called with an exception unrelated to the callback
 * processing it.
 */
public class CallbackGroup {
  private final List<CallbackGlue> callbacks;
  private final Set<CallbackGlue> remaining;
  private boolean finalAdded;

  private boolean failed;
  private Throwable failedThrowable;

  public static <T> Callback<T> emptyCallback() {
    return new Callback<T>() {
      @Override
      public void onSuccess(T result) {}

      @Override
      public void onFailure(Throwable err) {}
    };
  }

  public CallbackGroup() {
    callbacks = new ArrayList<>();
    remaining = new HashSet<>();
  }

  public <T> Callback<T> addEmpty() {
    Callback<T> cb = emptyCallback();
    return add(cb);
  }

  public <T> Callback<T> add(AsyncCallback<T> cb) {
    checkFinalAdded();
    return handleAdd(cb);
  }

  public <T> HttpCallback<T> add(HttpCallback<T> cb) {
    checkFinalAdded();
    return handleAdd(cb);
  }

  public <T> Callback<T> addFinal(AsyncCallback<T> cb) {
    checkFinalAdded();
    finalAdded = true;
    return handleAdd(cb);
  }

  public <T> HttpCallback<T> addFinal(HttpCallback<T> cb) {
    checkFinalAdded();
    finalAdded = true;
    return handleAdd(cb);
  }

  public void done() {
    finalAdded = true;
    apply();
  }

  public void addListener(AsyncCallback<Void> cb) {
    if (!failed && finalAdded && remaining.isEmpty()) {
      cb.onSuccess(null);
    } else {
      handleAdd(cb).onSuccess(null);
    }
  }

  public void addListener(CallbackGroup group) {
    addListener(group.<Void>addEmpty());
  }

  private void success(CallbackGlue cb) {
    remaining.remove(cb);
    apply();
  }

  private <T> void failure(CallbackGlue w, Throwable caught) {
    if (!failed) {
      failed = true;
      failedThrowable = caught;
    }
    remaining.remove(w);
    apply();
  }

  private void apply() {
    if (finalAdded && remaining.isEmpty()) {
      if (failed) {
        for (CallbackGlue cb : callbacks) {
          cb.applyFailed();
        }
      } else {
        for (CallbackGlue cb : callbacks) {
          cb.applySuccess();
        }
      }
      callbacks.clear();
    }
  }

  private <T> Callback<T> handleAdd(AsyncCallback<T> cb) {
    if (failed) {
      cb.onFailure(failedThrowable);
      return emptyCallback();
    }

    CallbackImpl<T> wrapper = new CallbackImpl<>(cb);
    callbacks.add(wrapper);
    remaining.add(wrapper);
    return wrapper;
  }

  private <T> HttpCallback<T> handleAdd(HttpCallback<T> cb) {
    if (failed) {
      cb.onFailure(failedThrowable);
      return new HttpCallback<T>() {
        @Override
        public void onSuccess(HttpResponse<T> result) {}

        @Override
        public void onFailure(Throwable caught) {}
      };
    }

    HttpCallbackImpl<T> w = new HttpCallbackImpl<>(cb);
    callbacks.add(w);
    remaining.add(w);
    return w;
  }

  private void checkFinalAdded() {
    if (finalAdded) {
      throw new IllegalStateException("final callback already added");
    }
  }

  public interface Callback<T>
      extends AsyncCallback<T>, com.google.gwtjsonrpc.common.AsyncCallback<T> {}

  private interface CallbackGlue {
    void applySuccess();

    void applyFailed();
  }

  private class CallbackImpl<T> implements Callback<T>, CallbackGlue {
    AsyncCallback<T> delegate;
    T result;

    CallbackImpl(AsyncCallback<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onSuccess(T value) {
      this.result = value;
      success(this);
    }

    @Override
    public void onFailure(Throwable caught) {
      failure(this, caught);
    }

    @Override
    public void applySuccess() {
      AsyncCallback<T> cb = delegate;
      if (cb != null) {
        delegate = null;
        cb.onSuccess(result);
        result = null;
      }
    }

    @Override
    public void applyFailed() {
      AsyncCallback<T> cb = delegate;
      if (cb != null) {
        delegate = null;
        result = null;
        cb.onFailure(failedThrowable);
      }
    }
  }

  private class HttpCallbackImpl<T> implements HttpCallback<T>, CallbackGlue {
    private HttpCallback<T> delegate;
    private HttpResponse<T> result;

    HttpCallbackImpl(HttpCallback<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onSuccess(HttpResponse<T> result) {
      this.result = result;
      success(this);
    }

    @Override
    public void onFailure(Throwable caught) {
      failure(this, caught);
    }

    @Override
    public void applySuccess() {
      HttpCallback<T> cb = delegate;
      if (cb != null) {
        delegate = null;
        cb.onSuccess(result);
        result = null;
      }
    }

    @Override
    public void applyFailed() {
      HttpCallback<T> cb = delegate;
      if (cb != null) {
        delegate = null;
        result = null;
        cb.onFailure(failedThrowable);
      }
    }
  }
}
