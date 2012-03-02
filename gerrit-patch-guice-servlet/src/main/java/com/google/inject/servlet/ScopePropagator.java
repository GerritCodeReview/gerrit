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

package com.google.inject.servlet;

/**
 * Interface for propagating request-scoped data between threads.
 * <p>
 * Request scopes are typically linked to a {@link ThreadLocal}, so requests
 * that spawn new threads to do background work by default do not have access to
 * request-scoped data. Thus, every scope implementation needs a corresponding
 * propagator implementation to copy that per-thread request state between
 * threads.
 */
public interface ScopePropagator {
  /** Propagator that does not propagate any data. */
  public static final ScopePropagator NO_PROPAGATION = new ScopePropagator() {
    @Override
    public Runnable wrapInCurrentScope(Runnable runnable) {
      return runnable;
    }
  };

  /**
   * Wrap a runnable in one that propagates the request state from the calling
   * thread to the thread in which the runnable runs.
   *
   * @param runnable the runnable to run in a separate thread.
   * @return a new runnable that runs the given runnable with the proper request
   *     scope set up.
   */
  public Runnable wrapInCurrentScope(Runnable runnable);
}
