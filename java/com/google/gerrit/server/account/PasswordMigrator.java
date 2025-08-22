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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.MultiProgressMonitor.TaskKind;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.project.LockManager;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.io.NullOutputStream;

public class PasswordMigrator implements Runnable {
  static final FluentLogger logger = FluentLogger.forEnclosingClass();
  @VisibleForTesting public static final String DEFAULT_ID = "default";

  private final GitRepositoryManager repoManager;
  private final MultiProgressMonitor.Factory multiProgressMonitorFactory;
  private final AuthTokenAccessor tokenAccessor;
  private final ExternalIds externalIds;
  private final ExternalIdFactory externalIdFactory;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
  private final AllUsersName allUsers;
  private final ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  private final Optional<Instant> expirationDate;
  private final LockManager lockManager;

  private MultiProgressMonitor mpm;
  private Task doneTask;
  private Task failedTask;

  public interface Factory {
    public PasswordMigrator create(Optional<Instant> expiryDate);
  }

  @AssistedInject
  public PasswordMigrator(
      GitRepositoryManager repoManager,
      MultiProgressMonitor.Factory multiProgressMonitorFactory,
      AuthTokenAccessor tokenAccessor,
      ExternalIds externalIds,
      ExternalIdFactory externalIdFactory,
      AllUsersName allUsers,
      ExternalIdNotes.FactoryNoReindex externalIdNotesFactory,
      Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory,
      @Assisted Optional<Instant> expirationDate,
      LockManager lockManager) {
    this.repoManager = repoManager;
    this.multiProgressMonitorFactory = multiProgressMonitorFactory;
    this.tokenAccessor = tokenAccessor;
    this.externalIds = externalIds;
    this.externalIdFactory = externalIdFactory;
    this.allUsers = allUsers;
    this.externalIdNotesFactory = externalIdNotesFactory;
    this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
    this.expirationDate = expirationDate;
    this.lockManager = lockManager;
  }

  @Override
  public void run() {
    Lock lock = lockManager.getLock("MigratePasswordsToTokens");
    if (!lock.tryLock()) {
      logger.atWarning().log("Migration of passwords to tokens already running.");
      return;
    }
    try {
      ImmutableSet<ExternalId> todo;
      try {
        todo = getAllUsernameExternalIds();
      } catch (IOException | ConfigInvalidException e) {
        logger.atSevere().withCause(e).log("Unable to read external IDs.");
        return;
      }

      if (todo.isEmpty()) {
        logger.atInfo().log("No accounts with HTTP passwords exist. Nothing to do.");
        return;
      }

      Stopwatch sw = Stopwatch.createStarted();
      mpm =
          multiProgressMonitorFactory.create(
              NullOutputStream.INSTANCE, TaskKind.MIGRATION, "Migrating HTTP passwords", true);
      doneTask = mpm.beginSubTask("passwords", todo.size());
      failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);
      List<ExternalId> failedToMigrate = new ArrayList<>();
      for (ExternalId extId : todo) {
        if (!createToken(extId, expirationDate)) {
          failedToMigrate.add(extId);
        }
        logger.atInfo().atMostEvery(30, TimeUnit.SECONDS).log(
            "Migrated %d/%d HTTP passwords (%d failed).",
            doneTask.getCount(), todo.size(), failedTask.getCount());
      }
      doneTask.end();
      failedTask.end();
      mpm.end();
      logger.atInfo().log(
          "Finished creating tokens. %d/%d finished successfully in %d min. %d tasks" + " failed.",
          doneTask.getCount(), todo.size(), sw.elapsed(TimeUnit.MINUTES), failedTask.getCount());
      logger.atInfo().log("Starting to delete HTTP passwords from External IDs.");
      try {
        deletePasswordsInExternalId(failedToMigrate);
      } catch (ConfigInvalidException e) {
        logger.atSevere().withCause(e).log(
            "Migration of HTTP passwords failed. Unable to load External IDs.");
      }
      sw.stop();
      logger.atInfo().log(
          "Finished deleting HTTP passwords from External IDs. Total migration to tokens took %d"
              + " min.",
          sw.elapsed(TimeUnit.MINUTES));
    } finally {
      lock.unlock();
    }
  }

  private boolean createToken(ExternalId extId, Optional<Instant> expirationDate) {
    String hashedPassword = extId.password();
    if (hashedPassword == null) {
      return true;
    }
    Account.Id accountId = extId.accountId();
    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Creating token from HTTP password",
            Metadata.builder().projectName("All-Users").accountId(accountId.get()).build())) {
      if (tokenAccessor.getToken(accountId, PasswordMigrator.DEFAULT_ID).isPresent()) {
        logger.atFine().log("HTTP password of account %d was already migrated.", accountId.get());
      } else {
        try {
          @SuppressWarnings("unused")
          var unused =
              tokenAccessor.addToken(
                  accountId, PasswordMigrator.DEFAULT_ID, hashedPassword, expirationDate);
        } catch (IOException | ConfigInvalidException | InvalidAuthTokenException e) {
          logger.atSevere().withCause(e).log(
              "Failed to migrate HTTP password to token for account %d", accountId.get());
          failedTask.update(1);
          return false;
        }
      }
      doneTask.update(1);
    }
    return true;
  }

  private void deletePasswordsInExternalId(List<ExternalId> exclude) throws ConfigInvalidException {

    try (TraceTimer traceTimer =
        TraceContext.newTimer(
            "Deleting Passwords from external IDs",
            Metadata.builder()
                .projectName("All-Users")
                .noteDbRefName("refs/meta/external-ids")
                .build())) {
      ImmutableSet<ExternalId> extIdsWithPassword;
      try {
        extIdsWithPassword = getAllUsernameExternalIds();
      } catch (IOException | ConfigInvalidException e) {
        logger.atSevere().withCause(e).log(
            "Unable to read external IDs. Can't delete passwords from external IDs.");
        return;
      }
      Set<ExternalId> extIdsToUpdate =
          extIdsWithPassword.stream().filter(e -> !exclude.contains(e)).collect(Collectors.toSet());
      Set<ExternalId> updatedExtIds =
          extIdsToUpdate.stream()
              .map(e -> externalIdFactory.createWithEmail(e.key(), e.accountId(), e.email()))
              .collect(Collectors.toSet());
      try (Repository repo = repoManager.openRepository(allUsers)) {
        ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
        extIdNotes.replaceByKeys(
            extIdsToUpdate.stream().map(e -> e.key()).collect(Collectors.toSet()), updatedExtIds);
        try (MetaDataUpdate metaDataUpdate = metaDataUpdateServerFactory.get().create(allUsers)) {
          metaDataUpdate.setMessage("Migrate HTTP passwords to tokens");
          extIdNotes.commit(metaDataUpdate);
        }
      } catch (IOException | DuplicateExternalIdKeyException e) {
        logger.atSevere().withCause(e).log("Unable to replace External IDs.");
      }
    }
  }

  private ImmutableSet<ExternalId> getAllUsernameExternalIds()
      throws IOException, ConfigInvalidException {
    return ImmutableSet.copyOf(
        externalIds.all().stream()
            .filter(e -> e.key().scheme().equals(SCHEME_USERNAME) && e.password() != null)
            .collect(Collectors.toSet()));
  }
}
