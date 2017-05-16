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

import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Throwables;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Singleton
public class RetryHelper {
  public interface Action<T> {
    T call(BatchUpdate.Factory updateFactory) throws Exception;
  }

  private final NotesMigration migration;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  RetryHelper(
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
  }

  public <T> T execute(Action<T> action) throws RestApiException, UpdateException {
    try {
      RetryerBuilder<T> builder = RetryerBuilder.newBuilder();
      if (migration.disableChangeReviewDb() && migration.fuseUpdates()) {
        // TODO(dborowitz): Make configurable.
        builder
            .withStopStrategy(StopStrategies.stopAfterDelay(20, TimeUnit.SECONDS))
            .withWaitStrategy(
                WaitStrategies.join(
                    WaitStrategies.exponentialWait(5, TimeUnit.SECONDS),
                    WaitStrategies.randomWait(50, TimeUnit.MILLISECONDS)))
            .retryIfException(RetryHelper::isLockFailure);
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
