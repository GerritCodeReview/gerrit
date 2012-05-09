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

import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.git.ProjectRunnable;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.servlet.ServletScopes;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Base class for propagating request-scoped data between threads.
 * <p>
 * Request scopes are typically linked to a {@link ThreadLocal}, which is only
 * available to the current thread.  In order to allow background work involving
 * RequestScoped data, the ThreadLocal data must be copied from the request thread to
 * the new background thread.
 * <p>
 * Every type of RequestScope must provide an implementation of
 * RequestScopePropagator. See {@link #wrap(Callable)} for details on the
 * implementation, usage, and restrictions.
 *
 * @see ThreadLocalRequestScopePropagator
 */
public abstract class RequestScopePropagator {

  private final Scope scope;
  private final ThreadLocalRequestContext local;

  protected RequestScopePropagator(Scope scope,
      ThreadLocalRequestContext local) {
    this.scope = scope;
    this.local = local;
  }

  /**
   * Wraps callable in a new {@link Callable} that propagates the current
   * request state when the callable is invoked. The method must be called in a
   * request scope and the returned Callable may only be invoked in a thread
   * that is not already in a request scope. The returned Callable will inherit
   * toString() from the passed in Callable. A
   * {@link com.google.gerrit.server.git.WorkQueue.Executor} does not accept a
   * Callable, so there is no ProjectCallable implementation. Implementations of
   * this method must be consistent with Guice's
   * {@link ServletScopes#continueRequest(Callable, java.util.Map)}.
   * <p>
   * There are some limitations:
   * <ul>
   * <li>Derived objects (i.e. anything marked created in a request scope) will
   * not be transported.</li>
   * <li>State changes to the request scoped context after this method is called
   * will not be seen in the continued thread.</li>
   * </ul>
   *
   * @param callable the Callable to wrap.
   * @return a new Callable which will execute in the current request scope.
   */
  public final <T> Callable<T> wrap(final Callable<T> callable) {
    final Callable<T> wrapped =
        wrapImpl(context(local.getContext(), cleanup(callable)));
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        return wrapped.call();
      }

      @Override
      public String toString() {
        return callable.toString();
      }
    };
  }

  /**
   * Wraps runnable in a new {@link Runnable} that propagates the current
   * request state when the runnable is invoked. The method must be called in a
   * request scope and the returned Runnable may only be invoked in a thread
   * that is not already in a request scope. The returned Runnable will inherit
   * toString() from the passed in Runnable. Furthermore, if the passed runnable
   * is of type {@link ProjectRunnable}, the returned runnable will be of the
   * same type with the methods delegated.
   *
   * See {@link #wrap(Callable)} for details on implementation and usage.
   *
   * @param runnable the Runnable to wrap.
   * @return a new Runnable which will execute in the current request scope.
   */
  public final Runnable wrap(final Runnable runnable) {
    final Callable<Object> wrapped = wrap(Executors.callable(runnable));

    if (runnable instanceof ProjectRunnable) {
      return new ProjectRunnable() {
        @Override
        public void run() {
          try {
            wrapped.call();
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException(e); // Not possible.
          }
        }

        @Override
        public NameKey getProjectNameKey() {
          return ((ProjectRunnable) runnable).getProjectNameKey();
        }

        @Override
        public String getRemoteName() {
          return ((ProjectRunnable) runnable).getRemoteName();
        }

        @Override
        public boolean hasCustomizedPrint() {
          return ((ProjectRunnable) runnable).hasCustomizedPrint();
        }

        @Override
        public String toString() {
          return runnable.toString();
        }
      };
    } else {
      return new Runnable() {
        @Override
        public void run() {
          try {
            wrapped.call();
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException(e); // Not possible.
          }
        }

        @Override
        public String toString() {
          return runnable.toString();
        }
      };
    }
  }

  /**
   * @see #wrap(Callable)
   */
  protected abstract <T> Callable<T> wrapImpl(final Callable<T> callable);

  protected <T> Callable<T> context(final RequestContext context,
      final Callable<T> callable) {
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        RequestContext old = local.setContext(context);
        try {
          return callable.call();
        } finally {
          local.setContext(old);
        }
      }
    };
  }

  protected <T> Callable<T> cleanup(final Callable<T> callable) {
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        RequestCleanup cleanup = scope.scope(
            Key.get(RequestCleanup.class),
            new Provider<RequestCleanup>() {
              @Override
              public RequestCleanup get() {
                return new RequestCleanup();
              }
            }).get();

        try {
          return callable.call();
        } finally {
          cleanup.run();
        }
      }
    };
  }
}
