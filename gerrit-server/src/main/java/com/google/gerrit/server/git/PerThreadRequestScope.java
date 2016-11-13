// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.server.config.RequestScopedReviewDbProvider;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class PerThreadRequestScope {
  public interface Scoper {
    <T> Callable<T> scope(Callable<T> callable);
  }

  private static class Context {
    private final Map<Key<?>, Object> map;

    private Context() {
      map = new HashMap<>();
    }

    private <T> T get(Key<T> key, Provider<T> creator) {
      @SuppressWarnings("unchecked")
      T t = (T) map.get(key);
      if (t == null) {
        t = creator.get();
        map.put(key, t);
      }
      return t;
    }
  }

  public static class Propagator extends ThreadLocalRequestScopePropagator<Context> {
    @Inject
    Propagator(
        ThreadLocalRequestContext local,
        Provider<RequestScopedReviewDbProvider> dbProviderProvider) {
      super(REQUEST, current, local, dbProviderProvider);
    }

    @Override
    protected Context continuingContext(Context ctx) {
      return new Context();
    }

    public <T> Callable<T> scope(RequestContext requestContext, Callable<T> callable) {
      final Context ctx = new Context();
      final Callable<T> wrapped = context(requestContext, cleanup(callable));
      return new Callable<T>() {
        @Override
        public T call() throws Exception {
          Context old = current.get();
          current.set(ctx);
          try {
            return wrapped.call();
          } finally {
            current.set(old);
          }
        }
      };
    }
  }

  private static final ThreadLocal<Context> current = new ThreadLocal<>();

  private static Context requireContext() {
    final Context ctx = current.get();
    if (ctx == null) {
      throw new OutOfScopeException("Not in command/request");
    }
    return ctx;
  }

  public static final Scope REQUEST =
      new Scope() {
        @Override
        public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
          return new Provider<T>() {
            @Override
            public T get() {
              return requireContext().get(key, creator);
            }

            @Override
            public String toString() {
              return String.format("%s[%s]", creator, REQUEST);
            }
          };
        }

        @Override
        public String toString() {
          return "PerThreadRequestScope.REQUEST";
        }
      };
}
