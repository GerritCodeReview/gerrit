// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbImpl;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.MultiProgressMonitor.TaskKind;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.project.LockManager;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.util.io.NullOutputStream;

public class MaxAuthTokenLifetimeApplier implements Runnable {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();
  @VisibleForTesting public static final String DEFAULT_ID = "default";

  private final MultiProgressMonitor.Factory multiProgressMonitorFactory;
  private final AuthTokenAccessor tokenAccessor;
  private final LockManager lockManager;
  private final AccountsNoteDbImpl accounts;
  private final Instant expiryInstant;

  private MultiProgressMonitor mpm;
  private Task doneTask;
  private Task failedTask;

  public interface Factory {
    public MaxAuthTokenLifetimeApplier create(Instant expiryInstant);
  }

  @AssistedInject
  public MaxAuthTokenLifetimeApplier(
      MultiProgressMonitor.Factory multiProgressMonitorFactory,
      AuthTokenAccessor tokenAccessor,
      LockManager lockManager,
      AccountsNoteDbImpl accounts,
      @Assisted Instant expiryInstant) {
    this.multiProgressMonitorFactory = multiProgressMonitorFactory;
    this.tokenAccessor = tokenAccessor;
    this.lockManager = lockManager;
    this.accounts = accounts;
    this.expiryInstant = expiryInstant;
  }

  @Override
  public void run() {
    Lock lock = lockManager.getLock("ReduceMaxAuthTokenLifetime");
    if (!lock.tryLock()) {
      logger.atWarning().log("Task applying limit to auth token lifetime already running.");
      return;
    }
    try {
      Set<Account.Id> todo = accounts.allIds();

      Stopwatch sw = Stopwatch.createStarted();
      mpm =
          multiProgressMonitorFactory.create(
              NullOutputStream.INSTANCE, TaskKind.MIGRATION, "Limiting auth token lifetime", true);
      doneTask = mpm.beginSubTask("done", MultiProgressMonitor.UNKNOWN);
      failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);
      for (Account.Id accountId : todo) {
        adaptTokenLifetime(accountId, expiryInstant);
        logger.atInfo().atMostEvery(30, TimeUnit.SECONDS).log(
            "Adapted lifetime of %d auth tokens (%d failed).",
            doneTask.getCount(), failedTask.getCount());
      }
      doneTask.end();
      failedTask.end();
      mpm.end();
      sw.stop();
      logger.atInfo().log(
          "Finished adapting lifetime of %d auth tokens. Total duration was %d min.",
          doneTask.getCount(), sw.elapsed(TimeUnit.MINUTES));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Could not load accounts.");
    } finally {
      lock.unlock();
    }
  }

  private void adaptTokenLifetime(Account.Id accountId, Instant maxAllowedExpirationInstant) {
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Limiting lifetime of auth tokens",
            Metadata.builder().projectName("All-Users").accountId(accountId.get()).build())) {
      for (AuthToken authToken : tokenAccessor.getTokens(accountId)) {
        if (authToken.expirationDate().isEmpty()
            || authToken.expirationDate().get().isAfter(maxAllowedExpirationInstant)) {
          AuthToken updatedToken;
          try {
            updatedToken =
                AuthToken.create(
                    authToken.id(),
                    authToken.hashedToken(),
                    Optional.of(maxAllowedExpirationInstant));
            tokenAccessor.updateToken(accountId, updatedToken);
            doneTask.update(1);
          } catch (InvalidAuthTokenException | IOException | ConfigInvalidException e) {
            logger.atSevere().withCause(e).log(
                "Failed to limit lifetime of token %s for account %d",
                authToken.id(), accountId.get());
            failedTask.update(1);
          }
        }
      }
    }
  }
}
