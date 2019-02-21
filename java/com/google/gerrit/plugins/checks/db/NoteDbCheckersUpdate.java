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

package com.google.gerrit.plugins.checks.db;

import com.google.common.base.Throwables;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerCreation;
import com.google.gerrit.plugins.checks.CheckerUpdate;
import com.google.gerrit.plugins.checks.CheckersUpdate;
import com.google.gerrit.plugins.checks.NoSuchCheckerException;
import com.google.gerrit.plugins.checks.api.CheckerStatus;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Class to write checkers to NoteDb. */
class NoteDbCheckersUpdate implements CheckersUpdate {
  interface Factory {
    /**
     * Creates a {@code CheckersUpdate} which uses the identity of the specified user to mark
     * database modifications executed by it. For NoteDb, this identity is used as author and
     * committer for all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of a {@code
     * CheckersUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed
     */
    NoteDbCheckersUpdate create(IdentifiedUser currentUser);

    /**
     * Creates a {@code CheckersUpdate} which uses the server identity to mark database
     * modifications executed by it. For NoteDb, this identity is used as author and committer for
     * all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of a {@code
     * CheckersUpdate} instead.
     */
    NoteDbCheckersUpdate createWithServerIdent();
  }

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  private final Optional<IdentifiedUser> currentUser;

  @AssistedInject
  NoteDbCheckersUpdate(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent) {
    this(
        repoManager,
        allProjectsName,
        metaDataUpdateInternalFactory,
        gitRefUpdated,
        retryHelper,
        serverIdent,
        Optional.empty());
  }

  @AssistedInject
  NoteDbCheckersUpdate(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted IdentifiedUser currentUser) {
    this(
        repoManager,
        allProjectsName,
        metaDataUpdateInternalFactory,
        gitRefUpdated,
        retryHelper,
        serverIdent,
        Optional.of(currentUser));
  }

  private NoteDbCheckersUpdate(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      Optional<IdentifiedUser> currentUser) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.currentUser = currentUser;

    metaDataUpdateFactory =
        getMetaDataUpdateFactory(metaDataUpdateInternalFactory, currentUser, serverIdent);
  }

  @Override
  public Checker createChecker(CheckerCreation checkerCreation, CheckerUpdate checkerUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.PLUGIN_UPDATE,
          () -> createCheckerInNoteDb(checkerCreation, checkerUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmDuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      throw new IOException(e);
    }
  }

  private Checker createCheckerInNoteDb(
      CheckerCreation checkerCreation, CheckerUpdate checkerUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      CheckerConfig checkerConfig =
          CheckerConfig.createForNewChecker(allProjectsName, allProjectsRepo, checkerCreation);
      checkerConfig.setCheckerUpdate(checkerUpdate);

      CheckersByRepositoryNotes checkersByRepositoryNotes =
          CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo);
      checkersByRepositoryNotes.insert(
          checkerCreation.getCheckerUuid(), checkerCreation.getRepository());

      commit(allProjectsRepo, checkerConfig, checkersByRepositoryNotes);

      return checkerConfig
          .getLoadedChecker()
          .orElseThrow(
              () -> new IllegalStateException("Created checker wasn't automatically loaded"));
    }
  }

  private void commit(
      Repository allProjectsRepo,
      CheckerConfig checkerConfig,
      CheckersByRepositoryNotes checkersByRepositoryNotes)
      throws IOException {
    BatchRefUpdate batchRefUpdate = allProjectsRepo.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate metaDataUpdate =
        metaDataUpdateFactory.create(allProjectsName, allProjectsRepo, batchRefUpdate)) {
      checkerConfig.commit(metaDataUpdate);
      checkersByRepositoryNotes.commit(metaDataUpdate);
    }
    RefUpdateUtil.executeChecked(batchRefUpdate, allProjectsRepo);

    gitRefUpdated.fire(
        allProjectsName, batchRefUpdate, currentUser.map(user -> user.state()).orElse(null));
  }

  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      Optional<IdentifiedUser> currentUser,
      PersonIdent serverIdent) {
    return (projectName, repository, batchRefUpdate) -> {
      MetaDataUpdate metaDataUpdate =
          metaDataUpdateInternalFactory.create(projectName, repository, batchRefUpdate);
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      PersonIdent authorIdent;
      if (currentUser.isPresent()) {
        metaDataUpdate.setAuthor(currentUser.get());
        authorIdent =
            currentUser.get().newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone());
      } else {
        authorIdent = serverIdent;
      }
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      return metaDataUpdate;
    };
  }

  @FunctionalInterface
  private interface MetaDataUpdateFactory {
    MetaDataUpdate create(
        Project.NameKey projectName, Repository repository, BatchRefUpdate batchRefUpdate)
        throws IOException;
  }

  @Override
  public Checker updateChecker(String checkerUuid, CheckerUpdate checkerUpdate)
      throws NoSuchCheckerException, IOException, ConfigInvalidException {
    Optional<Timestamp> updatedOn = checkerUpdate.getUpdatedOn();
    if (!updatedOn.isPresent()) {
      updatedOn = Optional.of(TimeUtil.nowTs());
      checkerUpdate = checkerUpdate.toBuilder().setUpdatedOn(updatedOn.get()).build();
    }
    return updateCheckerWithRetry(checkerUuid, checkerUpdate);
  }

  private Checker updateCheckerWithRetry(String checkerUuid, CheckerUpdate checkerUpdate)
      throws NoSuchCheckerException, IOException, ConfigInvalidException {
    try {
      return retryHelper.execute(
          ActionType.PLUGIN_UPDATE,
          () -> updateCheckerInNoteDb(checkerUuid, checkerUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, NoSuchCheckerException.class);
      throw new IOException(e);
    }
  }

  private Checker updateCheckerInNoteDb(String checkerUuid, CheckerUpdate checkerUpdate)
      throws IOException, ConfigInvalidException, NoSuchCheckerException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      CheckerConfig checkerConfig =
          CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
      checkerConfig.setCheckerUpdate(checkerUpdate);
      if (!checkerConfig.getLoadedChecker().isPresent()) {
        throw new NoSuchCheckerException(checkerUuid);
      }

      CheckersByRepositoryNotes checkersByRepositoryNotes =
          CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo);

      Checker checker = checkerConfig.getLoadedChecker().get();
      Project.NameKey oldRepositoryName = checker.getRepository();
      Project.NameKey newRepositoryName = checkerUpdate.getRepository().orElse(oldRepositoryName);

      CheckerStatus newStatus = checkerUpdate.getStatus().orElse(checker.getStatus());
      switch (newStatus) {
          // May produce some redundant notes updates, but CheckersByRepositoryNotes knows how to
          // short-circuit on no-ops, and the logic in this method is simple.
        case DISABLED:
          checkersByRepositoryNotes.remove(checkerUuid, oldRepositoryName);
          checkersByRepositoryNotes.remove(checkerUuid, newRepositoryName);
          break;
        case ENABLED:
          if (oldRepositoryName.equals(newRepositoryName)) {
            checkersByRepositoryNotes.insert(checkerUuid, newRepositoryName);
          } else {
            checkersByRepositoryNotes.update(checkerUuid, oldRepositoryName, newRepositoryName);
          }
          break;
        default:
          throw new IllegalStateException("invalid checker status: " + newStatus);
      }

      commit(allProjectsRepo, checkerConfig, checkersByRepositoryNotes);

      Checker updatedChecker =
          checkerConfig
              .getLoadedChecker()
              .orElseThrow(
                  () -> new IllegalStateException("Updated checker wasn't automatically loaded"));
      return updatedChecker;
    }
  }
}
