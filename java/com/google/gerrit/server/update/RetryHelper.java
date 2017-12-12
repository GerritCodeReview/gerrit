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

import com.github.rholder.retry.RetryerBuilder;
import com.google.common.base.Throwables;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RetryHelper extends AbstractRetryHelper {
  private final NotesMigration migration;
  private final BatchUpdate.Factory updateFactory;

  @Inject
  RetryHelper(
      @GerritServerConfig Config cfg,
      Metrics metrics,
      NotesMigration migration,
      ReviewDbBatchUpdate.AssistedFactory reviewDbBatchUpdateFactory,
      NoteDbBatchUpdate.AssistedFactory noteDbBatchUpdateFactory) {
    super(cfg, metrics);
    this.migration = migration;
    this.updateFactory =
        new BatchUpdate.Factory(migration, reviewDbBatchUpdateFactory, noteDbBatchUpdateFactory);
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
        return doExecute(updateFactory, action, RetryerBuilder.<T>newBuilder().build());
      }

      return doExecute(updateFactory, action, opts);
    } catch (Throwable t) {
      Throwables.throwIfInstanceOf(t, UpdateException.class);
      Throwables.throwIfInstanceOf(t, RestApiException.class);
      throw new UpdateException(t);
    }
  }

  @Override
  protected boolean retryOn(Throwable t) {
    if (t instanceof UpdateException) {
      t = t.getCause();
    }
    return t instanceof LockFailureException;
  }
}
