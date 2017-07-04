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

import static com.google.gerrit.server.account.ExternalId.SCHEME_GERRIT;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.ExternalId;
import com.google.gerrit.server.account.ExternalIdsBatchUpdate;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Collection;
import java.util.Locale;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Converts the local username for all accounts to lower case */
public class LocalUsernamesToLowerCase extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  @Inject private SchemaFactory<ReviewDb> database;

  @Inject private ExternalIdsBatchUpdate externalIdsBatchUpdate;

  @Override
  public int run() throws Exception {
    Injector dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector, dbInjector.createChildInjector(SchemaVersionCheck.module()));
    manager.start();
    dbInjector.injectMembers(this);

    try (ReviewDb db = database.open()) {
      Collection<ExternalId> todo = ExternalId.from(db.accountExternalIds().all().toList());
      monitor.beginTask("Converting local usernames", todo.size());

      for (ExternalId extId : todo) {
        convertLocalUserToLowerCase(extId);
        monitor.update(1);
      }

      externalIdsBatchUpdate.commit(db, "Convert local usernames to lower case");
    }
    monitor.endTask();

    int exitCode = reindexAccounts();
    manager.stop();
    return exitCode;
  }

  private void convertLocalUserToLowerCase(ExternalId extId) {
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
        externalIdsBatchUpdate.replace(extId, extIdLowerCase);
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
