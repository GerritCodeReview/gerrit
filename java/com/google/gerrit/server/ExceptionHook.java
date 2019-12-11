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
 * <p>This interface is intended to be implemented for cluster setups with multiple primary nodes to
 * control the behavior for handling exceptions that are thrown by a lower layer that handles the
 * consensus and synchronization between different server nodes. E.g. if an operation fails because
 * consensus for a Git update could not be achieved (e.g. due to slow responding server nodes) this
 * interface can be used to retry the request instead of failing it immediately.
 */
@ExtensionPoint
public interface ExceptionHook {
  /**
   * Whether an operation should be retried if it failed with the given throwable.
   *
   * <p>Only affects operations that are executed with {@link
   * com.google.gerrit.server.update.RetryHelper}.
   *
   * <p>Should return {@code true} only for exceptions that are caused by temporary issues where a
   * retry of the operation has a chance to succeed.
   *
   * <p>If {@code false} is returned the operation is still retried once to capture a trace, unless
   * {@link #skipRetryWithTrace(Throwable)} skips the auto-retry.
   *
   * @param throwable throwable that was thrown while executing the operation
   * @return whether the operation should be retried
   */
  default boolean shouldRetry(Throwable throwable) {
    return false;
  }

  /**
   * Whether auto-retrying of an operation with tracing should be skipped for the given throwable.
   *
   * <p>Only affects operations that are executed with {@link
   * com.google.gerrit.server.update.RetryHelper}.
   *
   * <p>This method is only called for exceptions for which the operation should not be retried
   * ({@link #shouldRetry(Throwable)} returned {@code false}).
   *
   * <p>By default this method returns {@code false}, so that by default traces for unexpected
   * exceptions are captured, which allows to investigate them.
   *
   * <p>Implementors may use this method to skip retry with tracing for exceptions that occur due to
   * known causes that are permanent and where a trace is not needed for the investigation. For
   * example, if an operation fails because persisted data is corrupt, it makes no sense to retry
   * the operation with a trace, because the trace will not help with fixing the corrupt data.
   *
   * <p>This method is only invoked if retry with tracing is enabled on the server ({@code
   * retry.retryWithTraceOnFailure} in {@code gerrit.config} is set to {@code true}).
   *
   * @param throwable throwable that was thrown while executing the operation
   * @return whether auto-retrying of an operation with tracing should be skipped for the given
   *     throwable
   */
  default boolean skipRetryWithTrace(Throwable throwable) {
    return false;
  }

  /**
   * Formats the cause of an exception for use in metrics.
   *
   * <p>This method allows implementors to group exceptions that have the same cause into one metric
   * bucket.
   *
   * @param throwable the exception cause
   * @return formatted cause or {@link Optional#empty()} if no formatting was done
   */
  default Optional<String> formatCause(Throwable throwable) {
    return Optional.empty();
  }

  /**
   * Returns a message that should be returned to the user.
   *
   * <p>This message is included into the HTTP response that is sent to the user.
   *
   * @param throwable throwable that was thrown while executing an operation
   * @return error message that should be returned to the user, {@link Optional#empty()} if no
   *     message should be returned to the user
   */
  default Optional<String> getUserMessage(Throwable throwable) {
    return Optional.empty();
  }

  /**
   * Returns the HTTP status code that should be returned to the user.
   *
   * <p>If no value is returned ({@link Optional#empty()}) the HTTP status code defaults to {@code
   * 500 Internal Server Error}.
   *
   * <p>{@link #getUserMessage(Throwable)} allows to define which message should be included into
   * the body of the HTTP response.
   *
   * <p>Implementors may use this method to change the status code for certain exceptions (e.g.
   * using this method it would be possible to return {@code 409 Conflict} for {@link
   * com.google.gerrit.git.LockFailureException}s instead of {@code 500 Internal Server Error}).
   *
   * @param throwable throwable that was thrown while executing an operation
   * @return HTTP status code that should be returned to the user, {@link Optional#empty()} if the
   *     exception should result in {@code 500 Internal Server Error}
   */
  default Optional<Integer> getStatusCode(Throwable throwable) {
    return Optional.empty();
  }
}
