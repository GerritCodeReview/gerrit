// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.servlet.ServletScopes;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class SimplifiedRequestScopePropagator {

  private final Scope scope;
  private final ThreadLocalRequestContext local;

  @Inject
  public SimplifiedRequestScopePropagator(ThreadLocalRequestContext local) {
    this.scope = ServletScopes.REQUEST;
    this.local = local;
  }

  public final <T> Callable<T> wrap(final Callable<T> callable) {
    final RequestContext callerContext = checkNotNull(local.getContext());
    final Callable<T> wrapped =
        wrapImpl(context(callerContext, cleanup(callable)));
    return new Callable<T>() {
      @Override
      public synchronized T call() throws Exception {
        if (callerContext == local.getContext()) {
          return callable.call();
        } else {
          return wrapped.call();
        }
      }

      @Override
      public String toString() {
        return callable.toString();
      }
    };
  }

  public final Runnable wrap(final Runnable runnable) {
    final Callable<Object> wrapped = wrap(Executors.callable(runnable));
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

  protected <T> Callable<T> context(final RequestContext context,
      final Callable<T> callable) {
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        RequestContext old = local.setContext(new RequestContext() {
          @Override
          public CurrentUser getCurrentUser() {
            return context.getCurrentUser();
          }

          @Override
          public Provider<ReviewDb> getReviewDbProvider() {
            return null;
          }
        });
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

  /**
   * @see RequestScopePropagator#wrap(Callable)
   */
  private <T> Callable<T> wrapImpl(Callable<T> callable) {
    Map<Key<?>, Object> seedMap = Maps.newHashMap();
    return ServletScopes.continueRequest(callable, seedMap);
  }
}
