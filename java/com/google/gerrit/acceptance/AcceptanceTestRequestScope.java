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

package com.google.gerrit.acceptance;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.HashMap;
import java.util.Map;

/** Guice scopes for state during an Acceptance Test connection. */
public class AcceptanceTestRequestScope {
  private static final Key<RequestCleanup> RC_KEY = Key.get(RequestCleanup.class);

  public static class Context implements RequestContext {
    private final RequestCleanup cleanup = new RequestCleanup();
    private final Map<Key<?>, Object> map = new HashMap<>();
    private final SshSession session;
    private final CurrentUser user;

    final long created;
    volatile long started;
    volatile long finished;

    private Context(SshSession s, CurrentUser u, long at) {
      session = s;
      user = u;
      created = started = finished = at;
      map.put(RC_KEY, cleanup);
    }

    private Context(Context p, SshSession s, CurrentUser c) {
      this(s, c, p.created);
      started = p.started;
      finished = p.finished;
    }

    public SshSession getSession() {
      return session;
    }

    @Override
    public CurrentUser getUser() {
      if (user == null) {
        throw new IllegalStateException("user == null, forgot to set it?");
      }
      return user;
    }

    synchronized <T> T get(Key<T> key, Provider<T> creator) {
      @SuppressWarnings("unchecked")
      T t = (T) map.get(key);
      if (t == null) {
        t = creator.get();
        map.put(key, t);
      }
      return t;
    }
  }

  static class ContextProvider implements Provider<Context> {
    @Override
    public Context get() {
      return requireContext();
    }
  }

  static class SshSessionProvider implements Provider<SshSession> {
    @Override
    public SshSession get() {
      return requireContext().getSession();
    }
  }

  static class Propagator extends ThreadLocalRequestScopePropagator<Context> {
    private final AcceptanceTestRequestScope atrScope;

    @Inject
    Propagator(AcceptanceTestRequestScope atrScope, ThreadLocalRequestContext local) {
      super(REQUEST, current, local);
      this.atrScope = atrScope;
    }

    @Override
    protected Context continuingContext(Context ctx) {
      // The cleanup is not chained, since the RequestScopePropagator executors
      // the Context's cleanup when finished executing.
      return atrScope.newContinuingContext(ctx);
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

  private final ThreadLocalRequestContext local;

  @Inject
  AcceptanceTestRequestScope(ThreadLocalRequestContext local) {
    this.local = local;
  }

  public Context newContext(SshSession s, CurrentUser user) {
    return new Context(s, user, TimeUtil.nowMs());
  }

  private Context newContinuingContext(Context ctx) {
    return new Context(ctx, ctx.getSession(), ctx.getUser());
  }

  public Context set(Context ctx) {
    Context old = current.get();
    current.set(ctx);
    local.setContext(ctx);
    return old;
  }

  public Context get() {
    return current.get();
  }

  public Context disableDb() {
    Context old = current.get();
    Context ctx = new Context(old.session, old.user, old.created);

    current.set(ctx);
    local.setContext(ctx);
    return old;
  }

  /** Returns exactly one instance per command executed. */
  static final Scope REQUEST =
      new Scope() {
        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> creator) {
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
          return "Acceptance Test Scope.REQUEST";
        }
      };
}
