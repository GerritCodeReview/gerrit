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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
   * {@link #skipRetryWithTrace(String, String, Throwable)} skips the auto-retry.
   *
   * <p>If multiple exception hooks are registered, the operation is retried if any of them returns
   * {@code true} from this method.
   *
   * @param throwable throwable that was thrown while executing the operation
   * @param actionType the type of the action for which the exception occurred
   * @param actionName the name of the action for which the exception occurred
   * @return whether the operation should be retried
   */
  default boolean shouldRetry(String actionType, String actionName, Throwable throwable) {
    return false;
  }

  /**
   * Whether auto-retrying of an operation with tracing should be skipped for the given throwable.
   *
   * <p>Only affects operations that are executed with {@link
   * com.google.gerrit.server.update.RetryHelper}.
   *
   * <p>This method is only called for exceptions for which the operation should not be retried
   * ({@link #shouldRetry(String, String, Throwable)} returned {@code false}).
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
   * <p>If multiple exception hooks are registered, retrying with tracing is skipped if any of them
   * returns {@code true} from this method.
   *
   * @param throwable throwable that was thrown while executing the operation
   * @param actionType the type of the action for which the exception occurred
   * @param actionName the name of the action for which the exception occurred
   * @return whether auto-retrying of an operation with tracing should be skipped for the given
   *     throwable
   */
  default boolean skipRetryWithTrace(String actionType, String actionName, Throwable throwable) {
    return false;
  }

  /**
   * Formats the cause of an exception for use in metrics.
   *
   * <p>This method allows implementors to group exceptions that have the same cause into one metric
   * bucket.
   *
   * <p>If multiple exception hooks return a value from this method, the value from the exception
   * hook that is registered first is used.
   *
   * @param throwable the exception cause
   * @return formatted cause or {@link Optional#empty()} if no formatting was done
   */
  default Optional<String> formatCause(Throwable throwable) {
    return Optional.empty();
  }

  /**
   * Returns messages that should be returned to the user.
   *
   * <p>These messages are included into the HTTP response that is sent to the user.
   *
   * <p>If multiple exception hooks return a value from this method, all the values are included
   * into the HTTP response (in the order in which the exception hooks are registered).
   *
   * @param throwable throwable that was thrown while executing an operation
   * @param traceIds trace IDs if this request was traced
   * @return error messages that should be returned to the user, {@link Optional#empty()} if no
   *     message should be returned to the user
   */
  default ImmutableList<String> getUserMessages(
      Throwable throwable, ImmutableSet<String> traceIds) {
    return ImmutableList.of();
  }

  /**
   * Returns the HTTP status that should be returned to the user.
   *
   * <p>Implementors may use this method to change the status for certain exceptions (e.g. using
   * this method it would be possible to return {@code 503 Lock failure} for {@link
   * com.google.gerrit.git.LockFailureException}s instead of {@code 500 Internal server error}).
   *
   * <p>If no value is returned ({@link Optional#empty()}) it means that this exception hook doesn't
   * want to change the default response code for the given exception which is {@code 500 Internal
   * Server Error}, but is fine if other exception hook implementation do so.
   *
   * <p>If multiple exception hooks return a value from this method, the value from exception hook
   * that is registered first is used.
   *
   * <p>{@link #getUserMessages(Throwable, ImmutableSet)} allows to define which additional messages
   * should be included into the body of the HTTP response.
   *
   * @param throwable throwable that was thrown while executing an operation
   * @return HTTP status that should be returned to the user, {@link Optional#empty()} if the
   *     exception should result in {@code 500 Internal Server Error}
   */
  default Optional<Status> getStatus(Throwable throwable) {
    return Optional.empty();
  }

  @AutoValue
  public abstract class Status {
    public abstract int statusCode();

    public abstract String statusMessage();

    public static Status create(int statusCode, String statusMessage) {
      return new AutoValue_ExceptionHook_Status(
          statusCode, requireNonNull(statusMessage, "statusMessage"));
    }
  }
}
