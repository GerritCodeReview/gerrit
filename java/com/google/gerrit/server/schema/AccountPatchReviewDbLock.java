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

package com.google.gerrit.server.schema;

import com.google.gerrit.common.Nullable;
import java.sql.SQLException;

/**
 * Plugin-provided locking strategy for {@link H2PluginLockAccountPatchReviewStore}.
 *
 * <p>Activated by setting {@code accountPatchReviewDb.h2LockType = plugin}. A plugin binds an
 * implementation via {@code DynamicItem.bind(binder(), AccountPatchReviewDbLock.class)}.
 */
public interface AccountPatchReviewDbLock {

  /**
   * Makes a single attempt to acquire the lock.
   *
   * @return a {@link Runnable} that releases the lock, or {@code null} if the lock is currently
   *     held by another process (retry-able)
   * @throws SQLException on a hard, non-retryable error
   */
  @Nullable
  Runnable tryAcquireLock() throws SQLException;
}
