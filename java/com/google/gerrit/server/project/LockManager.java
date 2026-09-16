// Copyright (C) 2017 The Android Open Source Project
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

import java.util.concurrent.locks.Lock;

/**
 * A global locking mechanism.
 *
 * <p>This is an interface because distributed setup may need something beyond an in-memory lock.
 *
 * <p>A Gerrit system consisting of a single Gerrit server only needs an in-memory lock manager
 * which is implemented by the DefaultLockManager.
 *
 * <p>A distributed setup, consisting of more than one Gerrit server, can implement a distributed
 * lock manager that provides global locks.
 */
public interface LockManager {
  /**
   * Returns a lock namespaced by {@code major}, {@code minor} and optional {@code args}, so that
   * two unrelated callers can never collide on the same lock key.
   *
   * <ul>
   *   <li>{@code major}: the top-level namespace requesting the lock. Either {@code "gerrit"} for
   *       Gerrit core, or {@code "plugins/<plugin_name>"} for a plugin.
   *   <li>{@code minor}: the name of the functionality within that namespace that the lock guards
   *       (e.g. {@code "change-cleanup"}).
   *   <li>{@code args}: the arguments to that functionality that further scope the lock (e.g. a
   *       project name). May be empty if the functionality doesn't need to scope the lock any
   *       further.
   * </ul>
   */
  public Lock getLock(String major, String minor, String... args);
}
