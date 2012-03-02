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

import com.google.inject.OutOfScopeException;

/**
 * Scope propagator implementation that uses a standard set, try/finally, revert
 * pattern.
 *
 * @param <C> "context" type that gets set before executing a runnable and reset
 *     to the previous value after.
 */
public abstract class AbstractScopePropagator<C> implements ScopePropagator {
  @Override
  public Runnable wrapInCurrentScope(final Runnable runnable) {
    final C ctx = getCurrentContext();
    return new Runnable() {
      @Override
      public void run() {
        C old = setContext(ctx);
        try {
          if (old == ctx) {
            throw new OutOfScopeException(
                "Attempted to propagate request scope to original request thread");
          }
          runnable.run();
        } finally {
          setContext(old);
        }
      }
    };
  }

  /** @return the current thread's context object */
  protected abstract C getCurrentContext();

  /**
   * Set the current thread's context object.
   *
   * @param ctx new context object.
   * @return previous context object.
   */
  protected abstract C setContext(C ctx);
}
