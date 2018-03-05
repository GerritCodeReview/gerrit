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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;

/** Converts the local username for all accounts to lower case */
public class LocalUsernamesToLowerCase extends SiteProgram {
  @Option(name = "--threads", usage = "Number of concurrent threads to run")
  private int threads = 2;

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();
  private List<AccountExternalId> todo;

  private Injector dbInjector;

  @Inject private SchemaFactory<ReviewDb> database;

  @Override
  public int run() throws Exception {
    if (threads <= 0) {
      threads = 1;
    }

    dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector, dbInjector.createChildInjector(SchemaVersionCheck.module()));
    manager.start();
    dbInjector.injectMembers(this);

    try (ReviewDb db = database.open()) {
      todo = db.accountExternalIds().all().toList();
      synchronized (monitor) {
        monitor.beginTask("Converting local usernames", todo.size());
      }
    }

    final List<Worker> workers = new ArrayList<>(threads);
    for (int tid = 0; tid < threads; tid++) {
      Worker t = new Worker();
      t.start();
      workers.add(t);
    }
    for (Worker t : workers) {
      t.join();
    }
    synchronized (monitor) {
      monitor.endTask();
    }
    manager.stop();
    return 0;
  }

  private void convertLocalUserToLowerCase(final ReviewDb db, final AccountExternalId extId) {
    if (extId.isScheme(AccountExternalId.SCHEME_GERRIT)) {
      final String localUser = extId.getSchemeRest();
      final String localUserLowerCase = localUser.toLowerCase(Locale.US);
      if (!localUser.equals(localUserLowerCase)) {
        final AccountExternalId.Key extIdKeyLowerCase =
            new AccountExternalId.Key(AccountExternalId.SCHEME_GERRIT, localUserLowerCase);
        final AccountExternalId extIdLowerCase =
            new AccountExternalId(extId.getAccountId(), extIdKeyLowerCase);
        try {
          db.accountExternalIds().insert(Collections.singleton(extIdLowerCase));
          db.accountExternalIds().delete(Collections.singleton(extId));
        } catch (OrmException error) {
          System.err.println("ERR " + error.getMessage());
        }
      }
    }
  }

  private AccountExternalId next() {
    synchronized (todo) {
      if (todo.isEmpty()) {
        return null;
      }
      return todo.remove(todo.size() - 1);
    }
  }

  private class Worker extends Thread {
    @Override
    public void run() {
      try (ReviewDb db = database.open()) {
        for (; ; ) {
          final AccountExternalId extId = next();
          if (extId == null) {
            break;
          }
          convertLocalUserToLowerCase(db, extId);
          synchronized (monitor) {
            monitor.update(1);
          }
        }
      } catch (OrmException e) {
        e.printStackTrace();
      }
    }
  }
}
