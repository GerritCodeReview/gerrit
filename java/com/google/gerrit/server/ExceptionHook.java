// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Optional;

/**
 * Allows implementors to control how certain exceptions should be handled.
 *
 * <p>This interface is intended to be implemented for multi-master setups to control the behavior
 * for handling exceptions that are thrown by a lower layer that handles the consensus and
 * synchronization between different server nodes. E.g. if an operation fails because consensus for
 * a Git update could not be achieved (e.g. due to slow responding server nodes) this interface can
 * be used to retry the request instead of failing it immediately.
 */
@ExtensionPoint
public interface ExceptionHook {
  /**
   * Whether an operation should be retried if it failed with the given throwable.
   *
   * <p>Only affects operations that are executed with {@link
   * com.google.gerrit.server.update.RetryHelper}.
   *
   * @param throwable throwable that was thrown while executing the operation
   * @return whether the operation should be retried
   */
  default boolean shouldRetry(Throwable throwable) {
    return false;
  }

  /**
   * Formats the cause of an exception for use in metrics.
   *
   * <p>This method allows implementors to group exceptions that have the same cause into one metric
   * bucket.
   *
   * @param throwable the exception cause
   * @return formatted cause or {@link Optional#empty()} if not formatting was done
   */
  default Optional<String> formatCause(Throwable throwable) {
    return Optional.empty();
  }
}
