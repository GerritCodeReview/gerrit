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

package com.google.gerrit.server.util;

import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.concurrent.Callable;

/**
 * {@link RequestScopePropagator} implementation for request scopes based on a {@link ThreadLocal}
 * context.
 *
 * @param <C> "context" type stored in the {@link ThreadLocal}.
 */
public abstract class ThreadLocalRequestScopePropagator<C> extends RequestScopePropagator {

  private final ThreadLocal<C> threadLocal;

  protected ThreadLocalRequestScopePropagator(
      Scope scope,
      ThreadLocal<C> threadLocal,
      ThreadLocalRequestContext local,
      Provider<RequestScopedReviewDbProvider> dbProviderProvider) {
    super(scope, local, dbProviderProvider);
    this.threadLocal = threadLocal;
  }

  /** @see RequestScopePropagator#wrap(Callable) */
  @Override
  protected final <T> Callable<T> wrapImpl(final Callable<T> callable) {
    final C ctx = continuingContext(requireContext());
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        C old = threadLocal.get();
        threadLocal.set(ctx);
        try {
          return callable.call();
        } finally {
          if (old != null) {
            threadLocal.set(old);
          } else {
            threadLocal.remove();
          }
        }
      }
    };
  }

  private C requireContext() {
    C context = threadLocal.get();
    if (context == null) {
      throw new OutOfScopeException("Cannot access scoped object");
    }
    return context;
  }

  /**
   * Returns a new context object based on the passed in context that has no request scoped objects
   * initialized.
   *
   * <p>Note that some code paths expect request-scoped objects like {@code CurrentUser} to be
   * constructible starting from just the context object returned by this method. For example, in
   * the SSH scope, the context includes the {@code SshSession}, which is used by {@code
   * SshCurrentUserProvider} to construct a new {@code CurrentUser} in the new thread.
   *
   * @param ctx the context to continue.
   * @return a new context.
   */
  protected abstract C continuingContext(C ctx);
}
