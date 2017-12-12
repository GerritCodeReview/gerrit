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
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Histogram0;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RetryHelper {
  public interface Action<I, O> {
    O call(I input) throws Exception;
  }

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

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder listener(RetryListener listener);

      public abstract Builder timeout(Duration timeout);

      public abstract Options build();
    }
  }

  @VisibleForTesting
  @Singleton
  public static class Metrics {
    final Histogram0 attemptCounts;
    final Counter0 timeoutCount;

    @Inject
    Metrics(MetricMaker metricMaker) {
      attemptCounts =
          metricMaker.newHistogram(
              "batch_update/retry_attempt_counts",
              new Description(
                      "Distribution of number of attempts made by RetryHelper"
                          + " (1 == single attempt, no retry)")
                  .setCumulative()
                  .setUnit("attempts"));
      timeoutCount =
          metricMaker.newCounter(
              "batch_update/retry_timeout_count",
              new Description("Number of executions of RetryHelper that ultimately timed out")
                  .setCumulative()
                  .setUnit("timeouts"));
    }
  }

  public static Options.Builder options() {
    return new AutoValue_RetryHelper_Options.Builder();
  }

  private static Options defaults() {
    return options().build();
  }

  private final NotesMigration migration;
  private final Metrics metrics;
  private final BatchUpdate.Factory updateFactory;
  private final Duration defaultTimeout;
  private final WaitStrategy waitStrategy;
  @Nullable private final Consumer<RetryerBuilder<?>> retryerStrategySetup;

  @Inject
  RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      NotesMigration migration,
      ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
      NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory) {
    this(cfg, metrics, migration, reviewDbBatchUpdateFactory, noteDbBatchUpdateFactory, null);
  }

  @VisibleForTesting
  public RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      NotesMigration migration,
      ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
      NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory,
      @Nullable Consumer<RetryerBuilder<?>> retryerSetup) {
    this.metrics = metrics;
    this.migration = migration;
    this.updateFactory =
        new BatchUpdate.Factory(migration, reviewDbBatchUpdateFactory, noteDbBatchUpdateFactory);
    this.defaultTimeout =
        Duration.ofMillis(
            cfg.getTimeUnit("noteDb", null, "retryTimeout", SECONDS.toMillis(20), MILLISECONDS));
    this.waitStrategy =
        WaitStrategies.join(
            WaitStrategies.exponentialWait(
                cfg.getTimeUnit("noteDb", null, "retryMaxWait", SECONDS.toMillis(5), MILLISECONDS),
                MILLISECONDS),
            WaitStrategies.randomWait(50, MILLISECONDS));
    this.retryerStrategySetup = retryerSetup;
  }

  public Duration getDefaultTimeout() {
    return defaultTimeout;
  }

  public <I, O> O execute(I input, Action<I, O> action)
      throws IOException, ConfigInvalidException, OrmException {
    try {
      return execute(input, action, defaults(), t -> t instanceof LockFailureException);
    } catch (Throwable t) {
      Throwables.throwIfInstanceOf(t, IOException.class);
      Throwables.throwIfInstanceOf(t, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(t, OrmException.class);
      throw new OrmException(t);
    }
  }

  public <T> T execute(Action<BatchUpdate.Factory, T> action)
      throws RestApiException, UpdateException {
    return execute(action, defaults());
  }

  public <T> T execute(Action<BatchUpdate.Factory, T> action, Options opts)
      throws RestApiException, UpdateException {
    try {
      if (!migration.disableChangeReviewDb()) {
        // Either we aren't full-NoteDb, or the underlying ref storage doesn't support atomic
        // transactions. Either way, retrying a partially-failed operation is not idempotent, so
        // don't do it automatically. Let the end user decide whether they want to retry.
        return execute(updateFactory, action, RetryerBuilder.<T>newBuilder().build());
      }

      return execute(
          updateFactory,
          action,
          opts,
          t -> {
            if (t instanceof UpdateException) {
              t = t.getCause();
            }
            return t instanceof LockFailureException;
          });
    } catch (Throwable t) {
      Throwables.throwIfInstanceOf(t, UpdateException.class);
      Throwables.throwIfInstanceOf(t, RestApiException.class);
      throw new UpdateException(t);
    }
  }

  private <I, O> O execute(
      I input, Action<I, O> action, Options opts, Predicate<Throwable> exceptionPredicate)
      throws Throwable {
    MetricListener listener = new MetricListener();
    try {
      RetryerBuilder<O> retryerBuilder = createRetryerBuilder(opts, exceptionPredicate);
      retryerBuilder.withRetryListener(listener);
      return execute(input, action, retryerBuilder.build());
    } finally {
      metrics.attemptCounts.record(listener.getAttemptCount());
    }
  }

  private <I, O> O execute(I input, Action<I, O> action, Retryer<O> retryer) throws Throwable {
    try {
      return retryer.call(() -> action.call(input));
    } catch (ExecutionException | RetryException e) {
      if (e instanceof RetryException) {
        metrics.timeoutCount.increment();
      }
      if (e.getCause() != null) {
        throw e.getCause();
      }
      throw e;
    }
  }

  private <O> RetryerBuilder<O> createRetryerBuilder(
      Options opts, Predicate<Throwable> exceptionPredicate) {
    RetryerBuilder<O> retryerBuilder =
        RetryerBuilder.<O>newBuilder().retryIfException(exceptionPredicate);
    if (opts.listener() != null) {
      retryerBuilder.withRetryListener(opts.listener());
    }

    if (retryerStrategySetup != null) {
      retryerStrategySetup.accept(retryerBuilder);
      return retryerBuilder;
    }

    return retryerBuilder
        .withStopStrategy(
            StopStrategies.stopAfterDelay(
                firstNonNull(opts.timeout(), defaultTimeout).toMillis(), MILLISECONDS))
        .withWaitStrategy(waitStrategy);
  }

  private static class MetricListener implements RetryListener {
    private long attemptCount;

    MetricListener() {
      attemptCount = 1;
    }

    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      attemptCount = attempt.getAttemptNumber();
    }

    long getAttemptCount() {
      return attemptCount;
    }
  }
}
