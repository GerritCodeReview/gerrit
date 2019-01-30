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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.server.verifier.VerifiersUpdate;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Class to write verifiers to NoteDb. */
public class NoteDbVerifiersUpdate implements VerifiersUpdate {
  public interface Factory {
    NoteDbVerifiersUpdate create(@Nullable IdentifiedUser currentUser);
  }

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  @Nullable private final IdentifiedUser currentUser;

  @Inject
  NoteDbVerifiersUpdate(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.currentUser = currentUser;

    metaDataUpdateFactory =
        getMetaDataUpdateFactory(metaDataUpdateInternalFactory, currentUser, serverIdent);
  }

  @Override
  public Verifier createGroup(VerifierCreation verifierCreation, VerifierUpdate verifierUpdate)
      throws IOException, ConfigInvalidException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.VERIFIER_UPDATE,
          () -> createVerifierInNoteDb(verifierCreation, verifierUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      throw new IOException(e);
    }
  }

  @VisibleForTesting
  public Verifier createVerifierInNoteDb(
      VerifierCreation verifierCreation, VerifierUpdate verifierUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
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
        allProjectsName, batchRefUpdate, currentUser != null ? currentUser.state() : null);
  }

  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      @Nullable IdentifiedUser currentUser,
      PersonIdent serverIdent) {
    return (projectName, repository, batchRefUpdate) -> {
      MetaDataUpdate metaDataUpdate =
          metaDataUpdateInternalFactory.create(projectName, repository, batchRefUpdate);
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      PersonIdent authorIdent;
      if (currentUser != null) {
        metaDataUpdate.setAuthor(currentUser);
        authorIdent =
            currentUser.newCommitterIdent(serverIdent.getWhen(), serverIdent.getTimeZone());
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
