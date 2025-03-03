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

package com.google.gerrit.server.update;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.query.group.InternalGroupQuery;
import com.google.gerrit.server.update.RetryableAction.Action;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.gerrit.server.update.RetryableChangeAction.ChangeAction;
import com.google.gerrit.server.update.RetryableIndexQueryAction.IndexQueryAction;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RetryHelper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /**
   * Options for retrying a single operation.
   *
   * <p>This class is similar in function to upstream's {@link RetryerBuilder}, but it exists as its
   * own class in Gerrit for several reasons:
   *
   * <ul>
   *   <li>Gerrit needs to support defaults for some of the options, such as a default timeout.
   *       {@code RetryerBuilder} doesn't support calling the same setter multiple times, so doing
   *       this with {@code RetryerBuilder} directly would not be easy.
   *   <li>Gerrit explicitly does not want callers to have full control over all possible options,
   *       so this class exposes a curated subset.
   * </ul>
   */
  @AutoValue
  public abstract static class Options {
    @Nullable
    abstract RetryListener listener();

    @Nullable
    abstract Duration timeout();

    abstract Optional<String> actionName();

    abstract Optional<Predicate<Throwable>> retryWithTrace();

    abstract Optional<Consumer<String>> onAutoTrace();

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder listener(RetryListener listener);

      public abstract Builder timeout(Duration timeout);

      public abstract Builder actionName(String caller);

      public abstract Builder retryWithTrace(Predicate<Throwable> exceptionPredicate);

      public abstract Builder onAutoTrace(Consumer<String> traceIdConsumer);

      public abstract Options build();
    }
  }

  @VisibleForTesting
  @Singleton
  public static class Metrics {
    final Counter3<String, String, String> attemptCounts;
    final Counter3<String, String, String> timeoutCount;
    final Counter3<String, String, String> autoRetryCount;
    final Counter3<String, String, String> failuresOnAutoRetryCount;

    @Inject
    @VisibleForTesting
    public Metrics(MetricMaker metricMaker) {
      Field<String> actionTypeField =
          Field.ofString("action_type", Metadata.Builder::actionType)
              .description("The type of the action that was retried.")
              .build();
      Field<String> operationNameField =
          Field.ofString("operation_name", Metadata.Builder::operationName)
              .description("The name of the operation that was retried.")
              .build();
      Field<String> originalCauseField =
          Field.ofString("cause", Metadata.Builder::cause)
              .description("The original cause that triggered the retry.")
              .build();
      Field<String> causeField =
          Field.ofString("cause", Metadata.Builder::cause)
              .description("The cause for the retry.")
              .build();

      attemptCounts =
          metricMaker.newCounter(
              "action/retry_attempt_count",
              new Description(
                      "Number of retry attempts made by RetryHelper to execute an action"
                          + " (0 == single attempt, no retry)")
                  .setCumulative()
                  .setUnit("attempts"),
              actionTypeField,
              operationNameField,
              originalCauseField);
      timeoutCount =
          metricMaker.newCounter(
              "action/retry_timeout_count",
              new Description(
                      "Number of action executions of RetryHelper that ultimately timed out")
                  .setCumulative()
                  .setUnit("timeouts"),
              actionTypeField,
              operationNameField,
              originalCauseField);
      autoRetryCount =
          metricMaker.newCounter(
              "action/auto_retry_count",
              new Description("Number of automatic retries with tracing")
                  .setCumulative()
                  .setUnit("retries"),
              actionTypeField,
              operationNameField,
              causeField);
      failuresOnAutoRetryCount =
          metricMaker.newCounter(
              "action/failures_on_auto_retry_count",
              new Description("Number of failures on auto retry")
                  .setCumulative()
                  .setUnit("failures"),
              actionTypeField,
              operationNameField,
              causeField);
    }
  }

  public static Options.Builder options() {
    return new AutoValue_RetryHelper_Options.Builder();
  }

  private final Config cfg;
  private final Metrics metrics;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<InternalAccountQuery> internalAccountQuery;
  private final Provider<InternalChangeQuery> internalChangeQuery;
  private final Provider<InternalGroupQuery> internalGroupQuery;
  private final PluginSetContext<ExceptionHook> exceptionHooks;
  private final Duration defaultTimeout;
  private final Map<String, Duration> defaultTimeouts;
  private final WaitStrategy waitStrategy;
  @Nullable private final Consumer<RetryerBuilder<?>> overwriteDefaultRetryerStrategySetup;
  private final boolean retryWithTraceOnFailure;

  @Inject
  RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      PluginSetContext<ExceptionHook> exceptionHooks,
      BatchUpdate.Factory updateFactory,
      Provider<InternalAccountQuery> internalAccountQuery,
      Provider<InternalChangeQuery> internalChangeQuery,
      Provider<InternalGroupQuery> internalGroupQuery) {
    this(
        cfg,
        metrics,
        updateFactory,
        internalAccountQuery,
        internalChangeQuery,
        internalGroupQuery,
        exceptionHooks,
        null);
  }

  @VisibleForTesting
  public RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      BatchUpdate.Factory updateFactory,
      Provider<InternalAccountQuery> internalAccountQuery,
      Provider<InternalChangeQuery> internalChangeQuery,
      Provider<InternalGroupQuery> internalGroupQuery,
      PluginSetContext<ExceptionHook> exceptionHooks,
      @Nullable Consumer<RetryerBuilder<?>> overwriteDefaultRetryerStrategySetup) {
    this.cfg = cfg;
    this.metrics = metrics;
    this.updateFactory = updateFactory;
    this.internalAccountQuery = internalAccountQuery;
    this.internalChangeQuery = internalChangeQuery;
    this.internalGroupQuery = internalGroupQuery;
    this.exceptionHooks = exceptionHooks;
    this.defaultTimeout =
        Duration.ofMillis(
            cfg.getTimeUnit("retry", null, "timeout", SECONDS.toMillis(20), MILLISECONDS));
    this.defaultTimeouts = new HashMap<>();
    Arrays.stream(ActionType.values())
        .forEach(
            at ->
                defaultTimeouts.put(
                    at.name(),
                    Duration.ofMillis(
                        cfg.getTimeUnit(
                            "retry",
                            at.name(),
                            "timeout",
                            SECONDS.toMillis(defaultTimeout.getSeconds()),
                            MILLISECONDS))));

    this.waitStrategy =
        WaitStrategies.join(
            WaitStrategies.exponentialWait(
                cfg.getTimeUnit("retry", null, "maxWait", SECONDS.toMillis(5), MILLISECONDS),
                MILLISECONDS),
            WaitStrategies.randomWait(50, MILLISECONDS));
    this.overwriteDefaultRetryerStrategySetup = overwriteDefaultRetryerStrategySetup;
    this.retryWithTraceOnFailure = cfg.getBoolean("retry", "retryWithTraceOnFailure", false);
  }

  /**
   * Creates an action that is executed with retrying when called.
   *
   * <p>This method allows to use a custom action type. If the action type is one of {@link
   * ActionType} the usage of {@link #action(ActionType, String, Action)} is preferred.
   *
   * <p>The action type is used as metric bucket and decides which default timeout is used.
   *
   * @param actionType the type of the action, used as metric bucket
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  @UsedAt(UsedAt.Project.GOOGLE)
  public <T> RetryableAction<T> action(String actionType, String actionName, Action<T> action) {
    return new RetryableAction<>(this, actionType, actionName, action);
  }

  /**
   * Creates an action that is executed with retrying when called.
   *
   * @param actionType the type of the action, used as metric bucket
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  public <T> RetryableAction<T> action(ActionType actionType, String actionName, Action<T> action) {
    return new RetryableAction<>(this, actionType, actionName, action);
  }

  /**
   * Creates an action for updating an account that is executed with retrying when called.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  public <T> RetryableAction<T> accountUpdate(String actionName, Action<T> action) {
    return new RetryableAction<>(this, ActionType.ACCOUNT_UPDATE, actionName, action);
  }

  /**
   * Creates an action for updating a change that is executed with retrying when called.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  public <T> RetryableAction<T> changeUpdate(String actionName, Action<T> action) {
    return new RetryableAction<>(this, ActionType.CHANGE_UPDATE, actionName, action);
  }

  /**
   * Creates an action for updating a change that is executed with retrying when called.
   *
   * <p>The change action gets a {@link BatchUpdate.Factory} provided that can be used to update the
   * change.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param changeAction the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableChangeAction#call()} to
   *     execute the action
   */
  public <T> RetryableChangeAction<T> changeUpdate(
      String actionName, ChangeAction<T> changeAction) {
    return new RetryableChangeAction<>(this, updateFactory, actionName, changeAction);
  }

  /**
   * Creates an action for updating a group that is executed with retrying when called.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  public <T> RetryableAction<T> groupUpdate(String actionName, Action<T> action) {
    return new RetryableAction<>(this, ActionType.GROUP_UPDATE, actionName, action);
  }

  /**
   * Creates an action for updating of plugin-specific data that is executed with retrying when
   * called.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param action the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableAction#call()} to execute
   *     the action
   */
  public <T> RetryableAction<T> pluginUpdate(String actionName, Action<T> action) {
    return new RetryableAction<>(this, ActionType.PLUGIN_UPDATE, actionName, action);
  }

  /**
   * Creates an action for querying the account index that is executed with retrying when called.
   *
   * <p>The index query action gets a {@link InternalAccountQuery} provided that can be used to
   * query the account index.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param indexQueryAction the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableIndexQueryAction#call()} to
   *     execute the action
   */
  public <T> RetryableIndexQueryAction<InternalAccountQuery, T> accountIndexQuery(
      String actionName, IndexQueryAction<T, InternalAccountQuery> indexQueryAction) {
    return new RetryableIndexQueryAction<>(
        this, internalAccountQuery, actionName, indexQueryAction);
  }

  /**
   * Creates an action for querying the change index that is executed with retrying when called.
   *
   * <p>The index query action gets a {@link InternalChangeQuery} provided that can be used to query
   * the change index.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param indexQueryAction the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableIndexQueryAction#call()} to
   *     execute the action
   */
  public <T> RetryableIndexQueryAction<InternalChangeQuery, T> changeIndexQuery(
      String actionName, IndexQueryAction<T, InternalChangeQuery> indexQueryAction) {
    return new RetryableIndexQueryAction<>(this, internalChangeQuery, actionName, indexQueryAction);
  }

  /**
   * Creates an action for querying the group index that is executed with retrying when called.
   *
   * <p>The index query action gets a {@link InternalGroupQuery} provided that can be used to query
   * the account index.
   *
   * @param actionName the name of the action, used as metric bucket
   * @param indexQueryAction the action that should be executed
   * @return the retryable action, callers need to call {@link RetryableIndexQueryAction#call()} to
   *     execute the action
   */
  public <T> RetryableIndexQueryAction<InternalGroupQuery, T> groupIndexQuery(
      String actionName, IndexQueryAction<T, InternalGroupQuery> indexQueryAction) {
    return new RetryableIndexQueryAction<>(this, internalGroupQuery, actionName, indexQueryAction);
  }

  /**
   * Returns the default timeout for an action type.
   *
   * <p>The default timeout for an action type is defined by the 'retry.<action-type>.timeout'
   * parameter in gerrit.config. If this parameter is not set the value from the 'retry.timeout'
   * parameter is used (if this is also not set we fall back to to a hard-coded timeout of 20s).
   *
   * <p>Callers can overwrite the default timeout by setting another timeout in the {@link Options},
   * see {@link Options#timeout()}.
   *
   * @param actionType the action type for which the default timeout should be retrieved
   * @return the default timeout for the given action type
   */
  Duration getDefaultTimeout(String actionType) {
    Duration timeout = defaultTimeouts.get(actionType);
    if (timeout != null) {
      return timeout;
    }
    return readDefaultTimeoutFromConfig(actionType);
  }

  /**
   * Thread-safe method to read and cache a default timeout from gerrit.config.
   *
   * <p>After reading the default timeout from gerrit.config it is cached in the {@link
   * #defaultTimeouts} map, so that it's read only once.
   *
   * @param actionType the action type for which the default timeout should be retrieved
   * @return the default timeout for the given action type
   */
  private synchronized Duration readDefaultTimeoutFromConfig(String actionType) {
    Duration timeout = defaultTimeouts.get(actionType);
    if (timeout != null) {
      // some other thread has read the default timeout from the config in the meantime
      return timeout;
    }
    timeout =
        Duration.ofMillis(
            cfg.getTimeUnit(
                "retry",
                actionType,
                "timeout",
                SECONDS.toMillis(defaultTimeout.getSeconds()),
                MILLISECONDS));
    defaultTimeouts.put(actionType, timeout);
    return timeout;
  }

  /**
   * Executes an action and records the number of attempts and the timeout as metrics.
   *
   * @param actionType the type of the action
   * @param action the action which should be executed and retried on failure
   * @param opts options for retrying the action on failure
   * @param exceptionPredicate predicate to control on which exception the action should be retried
   * @return the result of executing the action
   * @throws Exception any error or exception that made the action fail, callers are expected to
   *     catch and inspect this Throwable to decide carefully whether it should be re-thrown
   */
  <T> T execute(
      String actionType, Action<T> action, Options opts, Predicate<Throwable> exceptionPredicate)
      throws Exception {
    MetricListener listener = new MetricListener();
    try (TraceContext traceContext = TraceContext.open()) {
      RetryerBuilder<T> retryerBuilder =
          createRetryerBuilder(
              actionType,
              opts,
              t -> {
                String actionName = opts.actionName().orElse("N/A");
                String cause = formatCause(t);

                // Do not retry if retrying was already done and failed.
                if (Throwables.getCausalChain(t).stream()
                    .anyMatch(RetryException.class::isInstance)) {
                  return false;
                }

                // exceptionPredicate checks for temporary errors for which the operation should be
                // retried (e.g. LockFailure). The retry has good chances to succeed.
                if (exceptionPredicate.test(t)) {
                  logger.atFine().withCause(t).log(
                      "Retry: %s failed with possibly temporary error (cause = %s)",
                      actionName, cause);
                  return true;
                }

                // Exception hooks may identify additional exceptions for retry.
                if (exceptionHooks.stream()
                    .anyMatch(h -> h.shouldRetry(actionType, actionName, t))) {
                  logger.atFine().withCause(t).log(
                      "Retry: %s failed with possibly temporary error (cause = %s)",
                      actionName, cause);
                  return true;
                }

                // A non-recoverable failure occurred. Check if we should retry to capture a trace
                // of the failure. If a trace was already done there is no need to retry.
                if (retryWithTraceOnFailure
                    && opts.retryWithTrace().isPresent()
                    && opts.retryWithTrace().get().test(t)) {
                  // Exception hooks may identify exceptions for which retrying with trace should be
                  // skipped.
                  if (exceptionHooks.stream()
                      .anyMatch(h -> h.skipRetryWithTrace(actionType, actionName, t))) {
                    return false;
                  }

                  if (!TraceContext.isTracing()) {
                    String traceId = "retry-on-failure-" + new RequestId();
                    traceContext.addTag(RequestId.Type.TRACE_ID, traceId).forceLogging();
                    logger.atWarning().withCause(t).log(
                        "AutoRetry: %s failed, retry with tracing enabled (cause = %s)",
                        actionName, cause);
                    opts.onAutoTrace().ifPresent(c -> c.accept(traceId));
                    metrics.autoRetryCount.increment(actionType, actionName, cause);
                    return true;
                  }

                  // A non-recoverable failure occurred. We retried the operation with tracing
                  // enabled and it failed again. Log the failure so that admin can see if it
                  // differs from the failure that triggered the retry.
                  logger.atWarning().withCause(t).log(
                      "AutoRetry: auto-retry of %s has failed (cause = %s)", actionName, cause);
                  metrics.failuresOnAutoRetryCount.increment(actionType, actionName, cause);
                  return false;
                }

                return false;
              });
      retryerBuilder.withRetryListener(listener);
      return executeWithTimeoutCount(actionType, action, opts, retryerBuilder.build(), listener);
    } finally {
      if (listener.getAttemptCount() > 1) {
        logger.atWarning().log(
            "%s was attempted %d times",
            opts.actionName().isPresent() ? actionType + "." + opts.actionName().get() : actionType,
            listener.getAttemptCount());
        metrics.attemptCounts.incrementBy(
            actionType,
            opts.actionName().orElse("N/A"),
            listener.getOriginalCause().map(this::formatCause).orElse("_unknown"),
            listener.getAttemptCount() - 1);
      }
    }
  }

  public String formatCause(Throwable t) {
    while ((t instanceof UpdateException
            || t instanceof StorageException
            || t instanceof ExecutionException)
        && t.getCause() != null) {
      t = t.getCause();
    }

    Optional<String> formattedCause = getFormattedCauseFromHooks(t);
    if (formattedCause.isPresent()) {
      return formattedCause.get();
    }

    return t.getClass().getSimpleName();
  }

  private Optional<String> getFormattedCauseFromHooks(Throwable t) {
    return exceptionHooks.stream()
        .map(h -> h.formatCause(t))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }

  /**
   * Executes an action and records the timeout as metric.
   *
   * @param actionType the type of the action
   * @param action the action which should be executed and retried on failure
   * @param opts options for retrying the action on failure
   * @param retryer the retryer
   * @param listener metric listener
   * @return the result of executing the action
   * @throws Exception any exception that made the action fail, callers are expected to catch and
   *     inspect this exception to decide carefully whether it should be re-thrown
   */
  private <T> T executeWithTimeoutCount(
      String actionType,
      Action<T> action,
      Options opts,
      Retryer<T> retryer,
      MetricListener listener)
      throws Exception {
    try {
      return retryer.call(action::call);
    } catch (ExecutionException | RetryException e) {
      if (e instanceof RetryException) {
        metrics.timeoutCount.increment(
            actionType,
            opts.actionName().orElse("N/A"),
            listener.getOriginalCause().map(this::formatCause).orElse("_unknown"));

        // Re-throw the RetryException so that retrying is not re-attempted on an outer level.
        throw e;
      }
      if (e.getCause() != null) {
        Throwables.throwIfUnchecked(e.getCause());
        Throwables.throwIfInstanceOf(e.getCause(), Exception.class);
      }
      throw e;
    }
  }

  private <O> RetryerBuilder<O> createRetryerBuilder(
      String actionType, Options opts, Predicate<Throwable> exceptionPredicate) {
    RetryerBuilder<O> retryerBuilder =
        RetryerBuilder.<O>newBuilder().retryIfException(exceptionPredicate::test);
    if (opts.listener() != null) {
      retryerBuilder.withRetryListener(opts.listener());
    }

    if (overwriteDefaultRetryerStrategySetup != null) {
      overwriteDefaultRetryerStrategySetup.accept(retryerBuilder);
      return retryerBuilder;
    }

    return retryerBuilder
        .withStopStrategy(
            StopStrategies.stopAfterDelay(
                firstNonNull(opts.timeout(), getDefaultTimeout(actionType)).toMillis(),
                MILLISECONDS))
        .withWaitStrategy(waitStrategy);
  }

  private static class MetricListener implements RetryListener {
    private long attemptCount;
    private Optional<Throwable> originalCause;

    MetricListener() {
      attemptCount = 1;
      originalCause = Optional.empty();
    }

    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      attemptCount = attempt.getAttemptNumber();
      if (attemptCount == 1 && attempt.hasException()) {
        originalCause = Optional.of(attempt.getExceptionCause());
      }
    }

    long getAttemptCount() {
      return attemptCount;
    }

    Optional<Throwable> getOriginalCause() {
      return originalCause;
    }
  }
}
