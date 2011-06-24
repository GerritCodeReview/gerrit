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

import com.google.gerrit.server.RequestCleanup;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;

import java.util.HashMap;
import java.util.Map;

class PerThreadRequestScope {
  private static final ThreadLocal<PerThreadRequestScope> current =
      new ThreadLocal<PerThreadRequestScope>();

  private static PerThreadRequestScope getContext() {
    final PerThreadRequestScope ctx = current.get();
    if (ctx == null) {
      throw new OutOfScopeException("Not in command/request");
    }
    return ctx;
  }

  static PerThreadRequestScope set(PerThreadRequestScope ctx) {
    PerThreadRequestScope old = current.get();
    current.set(ctx);
    return old;
  }

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
      return "PerThreadRequestScope.REQUEST";
    }
  };

  private static final Key<RequestCleanup> RC_KEY =
      Key.get(RequestCleanup.class);

  final RequestCleanup cleanup;
  private final Map<Key<?>, Object> map;

  PerThreadRequestScope() {
    cleanup = new RequestCleanup();
    map = new HashMap<Key<?>, Object>();
    map.put(RC_KEY, cleanup);
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