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

import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.init.VersionedAuthTokensOnInit;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InstallAllPlugins;
import com.google.gerrit.pgm.init.api.InstallPlugins;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.account.AuthToken;
import com.google.gerrit.server.account.InvalidAuthTokenException;
import com.google.gerrit.server.account.VersionedAuthTokens;
import com.google.gerrit.server.account.externalids.storage.notedb.DisabledExternalIdCache;
import com.google.gerrit.server.account.storage.notedb.AccountsNoteDbImpl;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.restapi.account.CreateToken;
import com.google.gerrit.server.schema.NoteDbSchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

/** Reduces token lifetime if they exceed a given max lifetime */
public class ReduceMaxTokenLifetime extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  private Optional<Instant> lifetime = Optional.empty();

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private AccountsNoteDbImpl accounts;
  @Inject private VersionedAuthTokensOnInit.Factory tokenFactory;

  @Option(name = "--lifetime", usage = "The lifetime of migrated tokens.", required = true)
  public void setMaxLifetime(String value) throws BadRequestException {
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

                install(DisabledExternalIdCache.module());
              }
            })
        .injectMembers(this);

    monitor.beginTask("Collecting accounts", ProgressMonitor.UNKNOWN);
    Set<Account.Id> todo = accounts.allIds();
    monitor.endTask();

    monitor.beginTask("Adapting token lifetime", todo.size());
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      for (Account.Id accountId : todo) {
        adaptTokenLifetime(accountId, lifetime.orElse(Instant.MAX));
        monitor.update(1);
      }
    }
    monitor.endTask();

    manager.stop();
    return 0;
  }

  private void adaptTokenLifetime(Account.Id accountId, Instant maxAllowedExpirationInstant)
      throws IOException, ConfigInvalidException, InvalidAuthTokenException {
    VersionedAuthTokensOnInit authTokens = tokenFactory.create(accountId).load();
    boolean updated = false;
    for (AuthToken authToken : authTokens.getTokens()) {
      if (authToken.expirationDate().isEmpty()
          || authToken.expirationDate().get().isAfter(maxAllowedExpirationInstant)) {
        AuthToken updatedToken =
            AuthToken.create(
                authToken.id(), authToken.hashedToken(), Optional.of(maxAllowedExpirationInstant));
        authTokens.updateToken(updatedToken);
        updated = true;
      }
    }
    if (updated) {
      authTokens.save("Updated token lifetime");
    }
  }
}
