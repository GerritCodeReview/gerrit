// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.account.externalids.DisabledExternalIdCache;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Converts the local username for all accounts to lower case */
public class LocalUsernamesToLowerCase extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  @Inject private GitRepositoryManager repoManager;
  @Inject private AllUsersName allUsersName;
  @Inject private Provider<MetaDataUpdate.Server> metaDataUpdateServerFactory;
  @Inject private ExternalIdNotes.FactoryNoReindex externalIdNotesFactory;
  @Inject private ExternalIds externalIds;

  @Override
  public int run() throws Exception {
    Injector dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector, dbInjector.createChildInjector(SchemaVersionCheck.module()));
    manager.start();
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                factory(MetaDataUpdate.InternalFactory.class);

                // The LocalUsernamesToLowerCase program needs to access all external IDs only
                // once to update them. After the update they are not accessed again. Hence the
                // LocalUsernamesToLowerCase program doesn't benefit from caching external IDs and
                // the external ID cache can be disabled.
                install(DisabledExternalIdCache.module());
              }
            })
        .injectMembers(this);

    Collection<ExternalId> todo = externalIds.all();
    monitor.beginTask("Converting local usernames", todo.size());

    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ExternalIdNotes extIdNotes = externalIdNotesFactory.load(repo);
      for (ExternalId extId : todo) {
        convertLocalUserToLowerCase(extIdNotes, extId);
        monitor.update(1);
      }
      try (MetaDataUpdate metaDataUpdate = metaDataUpdateServerFactory.get().create(allUsersName)) {
        metaDataUpdate.setMessage("Convert local usernames to lower case");
        extIdNotes.commit(metaDataUpdate);
      }
    }

    monitor.endTask();

    int exitCode = reindexAccounts();
    manager.stop();
    return exitCode;
  }

  private void convertLocalUserToLowerCase(ExternalIdNotes extIdNotes, ExternalId extId)
      throws OrmDuplicateKeyException, IOException {
    if (extId.isScheme(SCHEME_GERRIT)) {
      String localUser = extId.key().id();
      String localUserLowerCase = localUser.toLowerCase(Locale.US);
      if (!localUser.equals(localUserLowerCase)) {
        ExternalId extIdLowerCase =
            ExternalId.create(
                SCHEME_GERRIT,
                localUserLowerCase,
                extId.accountId(),
                extId.email(),
                extId.password());
        extIdNotes.replace(extId, extIdLowerCase);
      }
    }
  }

  private int reindexAccounts() throws Exception {
    monitor.beginTask("Reindex accounts", ProgressMonitor.UNKNOWN);
    String[] reindexArgs = {
      "--site-path", getSitePath().toString(), "--index", AccountSchemaDefinitions.NAME
    };
    System.out.println("Migration complete, reindexing accounts with:");
    System.out.println("  reindex " + String.join(" ", reindexArgs));
    Reindex reindexPgm = new Reindex();
    int exitCode = reindexPgm.main(reindexArgs);
    monitor.endTask();
    return exitCode;
  }
}
