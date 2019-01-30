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

package com.google.gerrit.server.verifier.db;

import com.google.common.base.Throwables;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierCreation;
import com.google.gerrit.server.verifier.VerifierUpdate;
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Class to write verifiers to NoteDb. */
class NoteDbVerifiersUpdate implements VerifiersUpdate {
  interface Factory {
    /**
     * Creates a {@code VerifiersUpdate} which uses the identity of the specified user to mark
     * database modifications executed by it. For NoteDb, this identity is used as author and
     * committer for all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of a {@code
     * VerifiersUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed
     */
    NoteDbVerifiersUpdate create(IdentifiedUser currentUser);

    /**
     * Creates a {@code VerifiersUpdate} which uses the server identity to mark database
     * modifications executed by it. For NoteDb, this identity is used as author and committer for
     * all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of a {@code
     * VerifiersUpdate} instead.
     */
    NoteDbVerifiersUpdate createWithServerIdent();
  }

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  private final Optional<IdentifiedUser> currentUser;

  @AssistedInject
  NoteDbVerifiersUpdate(
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
  NoteDbVerifiersUpdate(
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

  private NoteDbVerifiersUpdate(
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
  public Verifier createVerifier(VerifierCreation verifierCreation, VerifierUpdate verifierUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.VERIFIER_UPDATE,
          () -> createVerifierInNoteDb(verifierCreation, verifierUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmDuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      throw new IOException(e);
    }
  }

  private Verifier createVerifierInNoteDb(
      VerifierCreation verifierCreation, VerifierUpdate verifierUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      VerifierConfig verifierConfig =
          VerifierConfig.createForNewVerifier(allProjectsName, allProjectsRepo, verifierCreation);
      verifierConfig.setVerifierUpdate(verifierUpdate);

      commit(allProjectsRepo, verifierConfig);

      return verifierConfig
          .getLoadedVerifier()
          .orElseThrow(
              () -> new IllegalStateException("Created verifier wasn't automatically loaded"));
    }
  }

  private void commit(Repository allProjectsRepo, VerifierConfig verifierConfig)
      throws IOException {
    BatchRefUpdate batchRefUpdate = allProjectsRepo.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate metaDataUpdate =
        metaDataUpdateFactory.create(allProjectsName, allProjectsRepo, batchRefUpdate)) {
      verifierConfig.commit(metaDataUpdate);
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
}
