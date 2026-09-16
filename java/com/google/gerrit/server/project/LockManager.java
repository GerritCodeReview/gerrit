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

import com.google.common.collect.Lists;
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
   * Enumeration of {@link LockManager} lock names owned by Gerrit core.
   *
   * <p>Centralizing core lock names here prevents two unrelated core call sites from silently
   * colliding on the same string. Plugins should not add entries; they should use {@link
   * PluginLockManager} instead.
   */
  enum CoreLock {
    CHANGE_CLEANUP("change-cleanup"),
    CREATE_PROJECT("create-project"),
    DRAFT_COMMENTS_CLEANUP("draft-comments-cleanup"),
    MIGRATE_PASSWORDS_TO_TOKENS("migrate-passwords-to-tokens"),
    REDUCE_MAX_AUTH_TOKEN_LIFETIME("reduce-max-auth-token-lifetime");

    private final String functionality;

    CoreLock(String functionality) {
      this.functionality = functionality;
    }

    String functionality() {
      return functionality;
    }
  }

  public Lock getLock(String name);

  /**
   * Returns a lock owned by Gerrit core, scoped by a well-known {@link CoreLock} and optional
   * {@code args} that further narrow the scope (e.g. a project name). The {@code "gerrit"}
   * namespace is implied. Plugins should use {@link PluginLockManager} instead.
   */
  default Lock getLock(CoreLock lock, String... args) {
    return getLock(String.join("~", Lists.asList("gerrit", lock.functionality(), args)));
  }
}
