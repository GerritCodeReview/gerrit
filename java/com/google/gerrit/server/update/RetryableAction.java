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

package com.google.gerrit.server.update;

import static java.util.Objects.requireNonNull;

import com.github.rholder.retry.RetryListener;
import com.google.common.base.Throwables;
import com.google.gerrit.server.ExceptionHook;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An action that is executed with retrying.
 *
 * <p>Instances of this class are created via {@link RetryHelper} (see {@link
 * RetryHelper#action(ActionType, String, Action)}, {@link RetryHelper#accountUpdate(String,
 * Action)}, {@link RetryHelper#changeUpdate(String, Action)}, {@link
 * RetryHelper#groupUpdate(String, Action)}, {@link RetryHelper#pluginUpdate(String, Action)}).
 *
 * <p>Which exceptions cause a retry is controlled by {@link ExceptionHook#shouldRetry(String,
 * String, Throwable)}. In addition callers can specify additional exception that should cause a
 * retry via {@link #retryOn(Predicate)}.
 */
public class RetryableAction<T> {
  /**
   * Type of an retryable action.
   *
   * <p>The action type is used for two purposes:
   *
   * <ul>
   *   <li>to determine the default timeout for executing the action (see {@link
   *       RetryHelper#getDefaultTimeout(String)})
   *   <li>as bucket for all retry metrics (see {@link RetryHelper.Metrics})
   * </ul>
   */
  public enum ActionType {
    ACCOUNT_UPDATE,
    CHANGE_UPDATE,
    GIT_UPDATE,
    GROUP_UPDATE,
    INDEX_QUERY,
    PLUGIN_UPDATE,
    REST_READ_REQUEST,
    REST_WRITE_REQUEST,
    SEND_EMAIL,
    SSH_REVIEW_CMD,
  }

  @FunctionalInterface
  public interface Action<T> {
    T call() throws Exception;
  }

  private final RetryHelper retryHelper;
  private final String actionType;
  private final Action<T> action;
  private final RetryHelper.Options.Builder options = RetryHelper.options();
  private final List<Predicate<Throwable>> exceptionPredicates = new ArrayList<>();

  RetryableAction(
      RetryHelper retryHelper, ActionType actionType, String actionName, Action<T> action) {
    this(retryHelper, requireNonNull(actionType, "actionType").name(), actionName, action);
  }

  RetryableAction(RetryHelper retryHelper, String actionType, String actionName, Action<T> action) {
    this.retryHelper = requireNonNull(retryHelper, "retryHelper");
    this.actionType = requireNonNull(actionType, "actionType");
    this.action = requireNonNull(action, "action");
    options.actionName(requireNonNull(actionName, "actionName"));
  }

  /**
   * Adds an additional condition that should trigger retries.
   *
   * <p>For some exceptions retrying is enabled globally (see {@link
   * ExceptionHook#shouldRetry(String, String, Throwable)}). Conditions for those exceptions do not
   * need to be specified here again.
   *
   * <p>This method can be invoked multiple times to add further conditions that should trigger
   * retries.
   *
   * @param exceptionPredicate predicate that decides if the action should be retried for a given
   *     exception
   * @return this instance to enable chaining of calls
   */
  public RetryableAction<T> retryOn(Predicate<Throwable> exceptionPredicate) {
    exceptionPredicates.add(exceptionPredicate);
    return this;
  }

  /**
   * Sets a condition that should trigger auto-retry with tracing.
   *
   * <p>This condition is only relevant if an exception occurs that doesn't trigger (normal) retry.
   *
   * <p>Auto-retry with tracing automatically captures traces for unexpected exceptions so that they
   * can be investigated.
   *
   * <p>Every call of this method overwrites any previously set condition for auto-retry with
   * tracing.
   *
   * @param exceptionPredicate predicate that decides if the action should be retried with tracing
   *     for a given exception
   * @return this instance to enable chaining of calls
   */
  public RetryableAction<T> retryWithTrace(Predicate<Throwable> exceptionPredicate) {
    options.retryWithTrace(exceptionPredicate);
    return this;
  }

  /**
   * Sets a callback that is invoked when auto-retry with tracing is triggered.
   *
   * <p>Via the callback callers can find out with trace ID was used for the retry.
   *
   * <p>Every call of this method overwrites any previously set trace ID consumer.
   *
   * @param traceIdConsumer trace ID consumer
   * @return this instance to enable chaining of calls
   */
  public RetryableAction<T> onAutoTrace(Consumer<String> traceIdConsumer) {
    options.onAutoTrace(traceIdConsumer);
    return this;
  }

  /**
   * Sets a listener that is invoked when the action is retried.
   *
   * <p>Every call of this method overwrites any previously set listener.
   *
   * @param retryListener retry listener
   * @return this instance to enable chaining of calls
   */
  public RetryableAction<T> listener(RetryListener retryListener) {
    options.listener(retryListener);
    return this;
  }

  /**
   * Increases the default timeout by the given multiplier.
   *
   * <p>Every call of this method overwrites any previously set timeout.
   *
   * @param multiplier multiplier for the default timeout
   * @return this instance to enable chaining of calls
   */
  public RetryableAction<T> defaultTimeoutMultiplier(int multiplier) {
    options.timeout(retryHelper.getDefaultTimeout(actionType).multipliedBy(multiplier));
    return this;
  }

  /**
   * Executes this action with retry.
   *
   * @return the result of the action
   */
  public T call() throws Exception {
    try {
      return retryHelper.execute(
          actionType,
          action,
          options.build(),
          t -> exceptionPredicates.stream().anyMatch(p -> p.test(t)));
    } catch (Exception t) {
      Throwables.throwIfUnchecked(t);
      Throwables.throwIfInstanceOf(t, Exception.class);
      throw new IllegalStateException(t);
    }
  }
}
