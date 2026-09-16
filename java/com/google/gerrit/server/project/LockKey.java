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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Objects;

/**
 * Identifies a {@link LockManager} lock: a {@code namespace} (Gerrit core, or a specific plugin), a
 * {@code function} for the functionality within that namespace that the lock guards (e.g. {@code
 * "change-cleanup"}), and optional {@code args} that further scope the lock (e.g. a project name).
 */
public record LockKey(String namespace, String function, ImmutableList<String> args) {
  private static final String CORE_NAMESPACE = "gerrit";
  private static final String PLUGIN_NAMESPACE_PREFIX = "plugins/";

  public LockKey {
    Objects.requireNonNull(namespace, "namespace cannot be null");
    Objects.requireNonNull(function, "function cannot be null");
  }

  public static LockKey of(String namespace, String function, String... args) {
    return new LockKey(namespace, function, ImmutableList.copyOf(args));
  }

  /** Returns a lock key owned by Gerrit core. */
  public static LockKey core(String name, String... args) {
    return of(CORE_NAMESPACE, name, args);
  }

  /** Returns a lock key owned by the plugin {@code pluginName}. */
  public static LockKey plugin(String pluginName, String function, String... args) {
    return of(PLUGIN_NAMESPACE_PREFIX + pluginName, function, args);
  }

  @Override
  public String toString() {
    return String.join("~", Lists.asList(namespace, function, args().toArray(new String[0])));
  }
}
