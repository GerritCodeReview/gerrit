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
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.google.auto.value.AutoValue;
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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RetryHelper {
  public interface Action<T> {
    T call(BatchUpdate.Factory updateFactory) throws Exception;
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

  @Singleton
  private static class Metrics {
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

  public static Options defaults() {
    return options().build();
  }

  private final NotesMigration migration;
  private final Metrics metrics;
  private final BatchUpdate.Factory updateFactory;
  private final Duration defaultTimeout;
  private final WaitStrategy waitStrategy;

  @Inject
  RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      NotesMigration migration,
      ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
      NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory) {
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
  }

  public Duration getDefaultTimeout() {
    return defaultTimeout;
  }

  public <T> T execute(Action<T> action) throws RestApiException, UpdateException {
    return execute(action, defaults());
  }

  public <T> T execute(Action<T> action, Options opts) throws RestApiException, UpdateException {
    MetricListener listener = null;
    try {
      RetryerBuilder<T> builder = RetryerBuilder.newBuilder();
      if (migration.disableChangeReviewDb()) {
        listener = new MetricListener(opts.listener());
        builder
            .withRetryListener(listener)
            .withStopStrategy(
                StopStrategies.stopAfterDelay(
                    firstNonNull(opts.timeout(), defaultTimeout).toMillis(), MILLISECONDS))
            .withWaitStrategy(waitStrategy)
            .retryIfException(RetryHelper::isLockFailure);
      } else {
        // Either we aren't full-NoteDb, or the underlying ref storage doesn't support atomic
        // transactions. Either way, retrying a partially-failed operation is not idempotent, so
        // don't do it automatically. Let the end user decide whether they want to retry.
      }
      return builder.build().call(() -> action.call(updateFactory));
    } catch (ExecutionException | RetryException e) {
      if (e instanceof RetryException) {
        metrics.timeoutCount.increment();
      }
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), UpdateException.class);
        Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      }
      throw new UpdateException(e);
    } finally {
      if (listener != null) {
        metrics.attemptCounts.record(listener.getAttemptCount());
      }
    }
  }

  private static boolean isLockFailure(Throwable t) {
    if (t instanceof UpdateException) {
      t = t.getCause();
    }
    return t instanceof LockFailureException;
  }

  private static class MetricListener implements RetryListener {
    private final RetryListener delegate;
    private long attemptCount;

    MetricListener(@Nullable RetryListener delegate) {
      this.delegate = delegate;
      attemptCount = 1;
    }

    @Override
    public <V> void onRetry(Attempt<V> attempt) {
      attemptCount = attempt.getAttemptNumber();
      if (delegate != null) {
        delegate.onRetry(attempt);
      }
    }

    long getAttemptCount() {
      return attemptCount;
    }
  }
}
