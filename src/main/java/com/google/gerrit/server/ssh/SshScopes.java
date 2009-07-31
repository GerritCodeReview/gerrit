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

package com.google.gerrit.server.ssh;

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import org.apache.sshd.common.session.AttributeKey;
import org.apache.sshd.server.session.ServerSession;

import java.util.HashMap;
import java.util.Map;

/** Guice scopes for state during an SSH connection. */
class SshScopes {
  static class Context {
    final ServerSession session;
    final Map<Key<?>, Object> map;

    Context(final ServerSession s) {
      session = s;
      map = new HashMap<Key<?>, Object>();
    }
  }

  static final AttributeKey<Map<Key<?>, Object>> sessionMap =
      new AttributeKey<Map<Key<?>, Object>>();

  static final ThreadLocal<Context> current = new ThreadLocal<Context>();

  static Context getContext() {
    final Context ctx = current.get();
    if (ctx == null) {
      throw new OutOfScopeException(
          "Cannot access scoped object; not in request/command.");
    }
    return ctx;
  }

  /** Returns exactly one instance for the scope of the SSH connection. */
  static final Scope SESSION = new Scope() {
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {
        public T get() {
          final Context ctx = getContext();
          final Map<Key<?>, Object> map = ctx.session.getAttribute(sessionMap);
          synchronized (map) {
            @SuppressWarnings("unchecked")
            T t = (T) map.get(key);
            if (t == null) {
              t = creator.get();
              map.put(key, t);
            }
            return t;
          }
        }

        @Override
        public String toString() {
          return String.format("%s[%s]", creator, SESSION);
        }
      };
    }

    @Override
    public String toString() {
      return "SshScopes.SESSION";
    }
  };

  /** Returns exactly one instance per command executed. */
  static final Scope REQUEST = new Scope() {
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {
        public T get() {
          final Map<Key<?>, Object> map = getContext().map;
          synchronized (map) {
            @SuppressWarnings("unchecked")
            T t = (T) map.get(key);
            if (t == null) {
              t = creator.get();
              map.put(key, t);
            }
            return t;
          }
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

  private SshScopes() {
  }
}
