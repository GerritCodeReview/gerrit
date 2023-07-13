// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.server.account.externalids.DuplicateExternalIdKeyException;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIdKeyFactory;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class ExternalIdCaseSensitivityMigrator {

  public static class ExternalIdCaseSensitivityMigratorModule extends AbstractModule {
    @Override
    public void configure() {
      install(new FactoryModuleBuilder().build(ExternalIdCaseSensitivityMigrator.Factory.class));
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    ExternalIdCaseSensitivityMigrator create(
        @Assisted("isUserNameCaseInsensitive") Boolean isUserNameCaseInsensitive,
        @Assisted("dryRun") Boolean dryRun);
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
  private final ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;

  private final ExternalIdFactory externalIdFactory;
  private final Boolean isUserNameCaseInsensitive;
  private final Boolean dryRun;

  @Inject
  public ExternalIdCaseSensitivityMigrator(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory,
      ExternalIdNotes.FactoryNoReindex externalIdNotesFactory,
      ExternalIdFactory externalIdFactory,
      @Assisted("isUserNameCaseInsensitive") Boolean isUserNameCaseInsensitive,
      @Assisted("dryRun") Boolean dryRun) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.metaDataUpdateServerFactory = metaDataUpdateServerFactory;
    this.externalIdNotesFactory = externalIdNotesFactory;
    this.externalIdFactory = externalIdFactory;

    this.isUserNameCaseInsensitive = isUserNameCaseInsensitive;
    this.dryRun = dryRun;
  }

  private void recomputeExternalIdNoteId(ExternalIdNotes extIdNotes, ExternalId extId)
      throws DuplicateKeyException, IOException {
    if (extId.isScheme(SCHEME_GERRIT) || extId.isScheme(SCHEME_USERNAME)) {
      ExternalIdKeyFactory keyFactory =
          new ExternalIdKeyFactory(
              new ExternalIdKeyFactory.Config() {
                @Override
                public boolean isUserNameCaseInsensitive() {
                  return isUserNameCaseInsensitive;
                }
              });
      ExternalId.Key updatedKey = keyFactory.create(extId.key().scheme(), extId.key().id());
      ExternalId.Key oldKey =
          keyFactory.create(extId.key().scheme(), extId.key().id(), !isUserNameCaseInsensitive);
      if (!oldKey.sha1().getName().equals(updatedKey.sha1().getName())
          && !extId.key().sha1().getName().equals(updatedKey.sha1().getName())) {
        logger.atInfo().log("Converting note name of external ID: %s", oldKey);
        ExternalId updatedExtId =
            externalIdFactory.create(
                updatedKey, extId.accountId(), extId.email(), extId.password(), extId.blobId());
        ExternalId oldExtId =
            externalIdFactory.create(
                oldKey, extId.accountId(), extId.email(), extId.password(), extId.blobId());
        extIdNotes.replace(
            Collections.singleton(oldExtId),
            Collections.singleton(updatedExtId),
            (externalId) -> externalId.key().sha1());
      }
    }
  }

  public void migrate(Collection<ExternalId> todo, Runnable monitor)
      throws RepositoryNotFoundException, IOException, ConfigInvalidException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
      for (ExternalId extId : todo) {
        recomputeExternalIdNoteId(extIdNotes, extId);
        monitor.run();
      }
      if (!dryRun) {
        try (MetaDataUpdate metaDataUpdate =
            metaDataUpdateServerFactory.get().create(allUsersName)) {
          metaDataUpdate.setMessage(
              String.format(
                  "Migration to case %ssensitive usernames",
                  isUserNameCaseInsensitive ? "" : "in"));
          extIdNotes.commit(metaDataUpdate);
        } catch (Exception e) {
          logger.atSevere().withCause(e).log("%s", e.getMessage());
        }
      }
    } catch (DuplicateExternalIdKeyException e) {
      logger.atSevere().withCause(e).log("%s", e.getMessage());
      throw e;
    }
  }
}
