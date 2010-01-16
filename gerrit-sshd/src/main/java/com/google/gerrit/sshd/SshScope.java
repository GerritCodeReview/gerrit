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

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.HashMap;
import java.util.Map;

/** Guice scopes for state during an SSH connection. */
class SshScope {
  static class Context {
    private final SshSession session;
    private final String commandLine;
    private final Map<Key<?>, Object> map;

    final long created;
    volatile long started;
    volatile long finished;

    Context(final SshSession s, final String c) {
      session = s;
      commandLine = c;
      map = new HashMap<Key<?>, Object>();

      final long now = System.currentTimeMillis();
      created = now;
      started = now;
      finished = now;
    }

    String getCommandLine() {
      return commandLine;
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
      return getContext();
    }
  }

  static class SshSessionProvider implements Provider<SshSession> {
    @Override
    public SshSession get() {
      return getContext().session;
    }
  }

  private static final ThreadLocal<Context> current =
      new ThreadLocal<Context>();

  private static Context getContext() {
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
          return getContext().get(key, creator);
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
