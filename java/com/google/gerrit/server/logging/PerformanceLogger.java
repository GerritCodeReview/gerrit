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

package com.google.gerrit.server.logging;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Extension point for logging performance records.
 *
 * <p>This extension point is invoked for all operations for which the execution time is measured.
 * The invocation of the extension point does not happen immediately, but only at the end of a
 * request (REST call, SSH call, git push). Implementors can write the execution times into a
 * performance log for further analysis.
 *
 * <p>For optimal performance implementors should overwrite the default <code>log</code> methods to
 * avoid an unneeded instantiation of Metadata.
 */
@ExtensionPoint
public interface PerformanceLogger {
  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   */
  default void log(String operation, long durationMs) {
    log(operation, durationMs, Metadata.empty());
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param metadata metadata
   */
  void log(String operation, long durationMs, Metadata metadata);
}
