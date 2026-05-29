// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.util.git;

import com.google.common.flogger.FluentLogger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Pool to manage resources that need to be closed but to whom we might lose the reference to or
 * where closing resources individually is not always possible.
 *
 * <p>This pool can be used when we want to reuse closable resources in a multithreaded context.
 * Example:
 *
 * <pre>{@code
 * try (CloseablePool<T> pool = new CloseablePool(() -> new T())) {
 *   for (int i = 0; i < 100; i++) {
 *     executor.submit(() -> {
 *       try (CloseablePool<T>.Handle handle = pool.get()) {
 *         // Do work that might potentially take longer than the timeout.
 *         handle.get(); // pooled instance to be used
 *       }
 *     }).get(1000, MILLISECONDS);
 *   }
 * }
 * }</pre>
 */
public class CloseablePool<T extends AutoCloseable> implements AutoCloseable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Supplier<T> tCreator;
  private List<T> ts;

  /**
   * Instantiate a new pool. The {@link Supplier} must be capable of creating a new instance on
   * every call.
   */
  public CloseablePool(Supplier<T> tCreator) {
    this.ts = new ArrayList<>();
    this.tCreator = tCreator;
  }

  /**
   * Get a shared instance or create a new instance. Close the returned handle to return it to the
   * pool.
   */
  public synchronized Handle get() {
    if (ts.isEmpty()) {
      return new Handle(tCreator.get());
    }
    return new Handle(ts.remove(ts.size() - 1));
  }

  private synchronized boolean discard(T t) {
    if (ts != null) {
      ts.add(t);
      return true;
    }
    return false;
  }

  @Override
  public synchronized void close() {
    for (T t : ts)
      try {
        t.close();
      } catch (Exception e) {
        logger.atWarning().withCause(e).log(
            "Failed to close resource %s in CloseablePool %s", t, this);
      }
    ts = null;
  }

  /**
   * Wrapper around an {@link AutoCloseable}. Will try to return the resource to the pool and close
   * it in case the pool was already closed.
   */
  public class Handle implements AutoCloseable {
    private final T t;

    private Handle(T t) {
      this.t = t;
    }

    /** Returns the managed instance. */
    public T get() {
      return t;
    }

    @Override
    public void close() {
      if (!discard(t)) {
        try {
          t.close();
        } catch (Exception e) {
          logger.atWarning().withCause(e).log(
              "Failed to close resource %s in CloseablePool %s", this, CloseablePool.this);
        }
      }
    }
  }
}
