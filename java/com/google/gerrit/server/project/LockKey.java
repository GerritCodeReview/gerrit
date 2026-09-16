// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Objects;

/**
 * Identifies a {@link LockManager} lock: a {@code namespace}, a {@code name}, and optional {@code
 * args} that further scope the lock (e.g. a project name).
 */
public record LockKey(String namespace, String name, ImmutableList<String> args) {
  private static final String CORE_NAMESPACE = "gerrit";
  private static final String PLUGIN_NAMESPACE_PREFIX = "plugins/";

  public LockKey {
    Objects.requireNonNull(namespace, "namespace cannot be null");
    Objects.requireNonNull(name, "name cannot be null");
  }

  /** Returns a lock key in the given namespace. */
  public static LockKey of(String namespace, String name, String... args) {
    return new LockKey(namespace, name, ImmutableList.copyOf(args));
  }

  /** Returns a lock key in the Gerrit core namespace. */
  public static LockKey core(String name, String... args) {
    return of(CORE_NAMESPACE, name, args);
  }

  /** Returns a lock key in the plugin/<pluginName> namespace. */
  public static LockKey plugin(String pluginName, String name, String... args) {
    return of(PLUGIN_NAMESPACE_PREFIX + pluginName, name, args);
  }

  @Override
  public String toString() {
    return Joiner.on('~').join(Iterables.concat(ImmutableList.of(namespace, name), args));
  }
}
