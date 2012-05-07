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

package com.google.gerrit.sshd;

import com.google.common.collect.Maps;
import com.google.gerrit.server.util.ThreadLocalRequestScopePropagator;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.Map;
import java.util.concurrent.Callable;

/** Guice scopes for state during an SSH connection. */
class SshScope {
  static class Context {
    private final SshSession session;
    private final String commandLine;
    private final Map<Key<?>, Object> map;

    final long created;
    volatile long started;
    volatile long finished;

    private Context(final SshSession s, final String c, final long at) {
      session = s;
      commandLine = c;
      map = Maps.newHashMap();
      created = started = finished = at;
    }

    private Context(Context p, SshSession s, String c) {
      this(s, c, p.created);
      started = p.started;
      finished = p.finished;
    }

    Context(final SshSession s, final String c) {
      this(s, c, System.currentTimeMillis());
    }

    String getCommandLine() {
      return commandLine;
    }

    SshSession getSession() {
      return session;
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

    Context subContext(SshSession newSession, String newCommandLine) {
      return new Context(this, newSession, newCommandLine);
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
    Propagator() {
      super(REQUEST, current);
    }

    @Override
    protected Context continuingContext(Context ctx) {
      // The cleanup is not chained, since the RequestScopePropagator executors
      // the Context's cleanup when finished executing.
      return new Context(ctx, ctx.getSession(), ctx.getCommandLine());
    }

    <T> Callable<T> wrap(final Context newContext, final Callable<T> callable) {
      return new Callable<T>() {
        @Override
        public T call() throws Exception {
          Context old = set(newContext);
          try {
            return wrapCleanup(callable).call();
          } finally {
            set(old);
          }
        }

      };
    }
  }

  private static final ThreadLocal<Context> current =
      new ThreadLocal<Context>();

  private static Context requireContext() {
    final Context ctx = current.get();
    if (ctx == null) {
      throw new OutOfScopeException("Not in command/request");
    }
    return ctx;
  }

  static Context set(Context ctx) {
    Context old = current.get();
    current.set(ctx);
    return old;
  }

  /** Returns exactly one instance per command executed. */
  static final Scope REQUEST = new Scope() {
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {
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
      return "SshScopes.REQUEST";
    }
  };

  private SshScope() {
  }
}
