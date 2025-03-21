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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.restapi.account.PutHttpPassword.DEFAULT_ID;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.init.VersionedAuthTokensOnInit;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.account.VersionedAuthTokens;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdFactory;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.DisabledExternalIdCache;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNoteDbReadStorageModule;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNoteDbWriteStorageModule;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNotes;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.restapi.account.CreateToken;
import com.google.gerrit.server.schema.NoteDbSchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

/** Converts HTTP passwords for all accounts to tokens */
public class MigratePasswordsToTokens extends SiteProgram {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  private Optional<Instant> lifetime = Optional.empty();

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
  @Inject private ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  @Inject private ExternalIdFactory externalIdFactory;
  @Inject private ExternalIds externalIds;
  @Inject private VersionedAuthTokensOnInit.Factory tokenFactory;

  @Option(name = "--lifetime", usage = "The lifetime of migrated tokens.")
  public void setDefaultLifetime(String value) throws BadRequestException {
    lifetime = CreateToken.getExpirationInstant(value, Optional.empty());
  }

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    ConsoleUI ui = ConsoleUI.getInstance();

    Injector dbInjector = createDbInjector();
    manager.add(dbInjector, dbInjector.createChildInjector(NoteDbSchemaVersionCheck.module()));
    manager.start();
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                bind(ConsoleUI.class).toInstance(ui);
                bind(Boolean.class)
                    .annotatedWith(InstallAllPlugins.class)
                    .toInstance(Boolean.FALSE);
                bind(new TypeLiteral<List<String>>() {})
                    .annotatedWith(InstallPlugins.class)
                    .toInstance(new ArrayList<>());

                factory(MetaDataUpdate.InternalFactory.class);
                factory(VersionedAuthTokens.Factory.class);
                factory(VersionedAuthTokensOnInit.Factory.class);
                factory(Section.Factory.class);

                install(new ExternalIdNoteDbReadStorageModule());
                install(new ExternalIdNoteDbWriteStorageModule());
                install(DisabledExternalIdCache.module());
              }
            })
        .injectMembers(this);

    monitor.beginTask("Collecting accounts", ProgressMonitor.UNKNOWN);
    ImmutableSet<ExternalId> todo =
        ImmutableSet.copyOf(
            externalIds.all().stream()
                .filter(e -> e.key().scheme().equals(SCHEME_USERNAME) && e.password() != null)
                .collect(Collectors.toSet()));
    monitor.endTask();

    if (todo.isEmpty()) {
      logger.atInfo().log("No accounts with HTTP passwords exist. Nothing to do.");
      return 0;
    }

    monitor.beginTask("Migrating HTTP passwords", todo.size());
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
      for (ExternalId extId : todo) {
        migratePasswordToToken(extId, extIdNotes);
        monitor.update(1);
      }
      try (MetaDataUpdate metaDataUpdate = metaDataUpdateServerFactory.get().create(allUsersName)) {
        metaDataUpdate.setMessage("Migrate HTTP passwords to tokens");
        extIdNotes.commit(metaDataUpdate);
      }
    }

    monitor.endTask();

    manager.stop();
    return 0;
  }

  private void migratePasswordToToken(ExternalId extId, ExternalIdNotes extIdNotes)
      throws DuplicateKeyException, IOException, ConfigInvalidException, InvalidAuthTokenException {
    String hashedPassword = extId.password();
    Account.Id accountId = extId.accountId();
    VersionedAuthTokensOnInit authTokens = tokenFactory.create(accountId).load();
    if (authTokens.getToken(DEFAULT_ID) != null) {
      logger.atWarning().log(
          "Account %d has already a legacy token, not adding another one", accountId.get());
      return;
    }
    authTokens.addToken(DEFAULT_ID, hashedPassword, lifetime);
    authTokens.save("Migration of HTTP password to token");

    ExternalId updatedExtId =
        externalIdFactory.createWithEmail(extId.key(), extId.accountId(), extId.email());
    extIdNotes.replace(extId, updatedExtId);
  }
}
