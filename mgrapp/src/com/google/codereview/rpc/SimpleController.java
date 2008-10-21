// Copyright 2008 Google Inc.
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

package com.google.codereview.rpc;

import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcController;

import java.util.Random;

/**
 * A simple controller which does not support cancellation.
 * <p>
 * Users should check {@link #failed()} to determine if a call failed.
 */
public class SimpleController implements RpcController {
  private static final int SLEEP_MIN = 100; // milliseconds
  private static final int SLEEP_MAX = 2 * 60 * 1000; // milliseconds
  private static final int MAX_ATTEMPT_PERIOD = 30 * 60 * 1000; // milliseconds
  private static final ThreadLocal<Random> SLEEP_RNG =
      new ThreadLocal<Random>() {
        @Override
        protected Random initialValue() {
          return new Random();
        }
      };

  private static int waitTime() {
    return SLEEP_MIN + SLEEP_RNG.get().nextInt(SLEEP_MAX - SLEEP_MIN);
  }

  private String errorText;
  private long start;

  public String errorText() {
    return errorText;
  }

  public boolean failed() {
    return errorText != null;
  }

  public void reset() {
    errorText = null;
  }

  public void setFailed(final String reason) {
    errorText = reason;
  }

  public void startCancel() {
    throw new UnsupportedOperationException();
  }

  public boolean isCanceled() {
    return false;
  }

  public void notifyOnCancel(final RpcCallback<Object> callback) {
    throw new UnsupportedOperationException();
  }

  boolean retry() {
    if (System.currentTimeMillis() - start < MAX_ATTEMPT_PERIOD) {
      try {
        Thread.sleep(waitTime());
      } catch (InterruptedException ie) {
        // Just let the thread continue anyway.
      }
      return true;
    }

    if (start == 0) {
      setFailed("retry not supported by the RpcChannel");
      return false;
    }

    final int s = MAX_ATTEMPT_PERIOD / 1000;
    setFailed("cannot complete in <" + s + " seconds");
    return false;
  }

  void markFirstRequest() {
    start = System.currentTimeMillis();
  }
}
