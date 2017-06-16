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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.StopStrategy;
import com.github.rholder.retry.WaitStrategies;
import com.github.rholder.retry.WaitStrategy;
import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RetryHelper {
  public interface Action<T> {
    T call(BatchUpdate.Factory updateFactory) throws Exception;
  }

  private final NotesMigration migration;
  private final BatchUpdate.Factory updateFactory;
  private final StopStrategy stopStrategy;
  private final WaitStrategy waitStrategy;

  @Inject
  RetryHelper(
      @GerritServerConfig Config cfg,
      NotesMigration migration,
      ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
      FusedNoteDbBatchUpdate.AssistedFactory fusedNoteDbBatchUpdateFactory,
      UnfusedNoteDbBatchUpdate.AssistedFactory unfusedNoteDbBatchUpdateFactory) {
    this.migration = migration;
    this.updateFactory =
        new BatchUpdate.Factory(
            migration,
            reviewDbBatchUpdateFactory,
            fusedNoteDbBatchUpdateFactory,
            unfusedNoteDbBatchUpdateFactory);
    this.stopStrategy =
        StopStrategies.stopAfterDelay(
            cfg.getTimeUnit("noteDb", null, "retryTimeout", SECONDS.toMillis(5), MILLISECONDS),
            MILLISECONDS);
    this.waitStrategy =
        WaitStrategies.join(
            WaitStrategies.exponentialWait(
                cfg.getTimeUnit("noteDb", null, "retryMaxWait", SECONDS.toMillis(20), MILLISECONDS),
                MILLISECONDS),
            WaitStrategies.randomWait(50, MILLISECONDS));
  }

  public <T> T execute(Action<T> action) throws RestApiException, UpdateException {
    return execute(action, null);
  }

  public <T> T execute(Action<T> action, @Nullable RetryListener listener)
      throws RestApiException, UpdateException {
    try {
      RetryerBuilder<T> builder = RetryerBuilder.newBuilder();
      if (migration.disableChangeReviewDb() && migration.fuseUpdates()) {
        builder
            .withStopStrategy(stopStrategy)
            .withWaitStrategy(waitStrategy)
            .retryIfException(RetryHelper::isLockFailure);
        if (listener != null) {
          builder.withRetryListener(listener);
        }
      } else {
        // Either we aren't full-NoteDb, or the underlying ref storage doesn't support atomic
        // transactions. Either way, retrying a partially-failed operation is not idempotent, so
        // don't do it automatically. Let the end user decide whether they want to retry.
      }
      return builder.build().call(() -> action.call(updateFactory));
    } catch (ExecutionException | RetryException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), UpdateException.class);
        Throwables.throwIfInstanceOf(e.getCause(), RestApiException.class);
      }
      throw new UpdateException(e);
    }
  }

  private static boolean isLockFailure(Throwable t) {
    if (t instanceof UpdateException) {
      t = t.getCause();
    }
    return t instanceof LockFailureException;
  }
}
