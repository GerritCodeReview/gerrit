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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.index.IndexingException;
import com.google.gerrit.server.index.LuceneIndex;
import com.google.gerrit.server.index.UpdateTransaction;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.kohsuke.args4j.Option;

import java.util.List;

/** Export review notes for all submitted changes in all projects. */
public class Reindex extends SiteProgram {
  @Option(name = "--threads", usage = "Number of concurrent threads to run")
  private int threads = 1;

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor textMonitor = new TextProgressMonitor();
  private final ThreadSafeProgressMonitor monitor =
      new ThreadSafeProgressMonitor(textMonitor);

  private Injector dbInjector;

  @Inject
  private SchemaFactory<ReviewDb> database;

  @Inject
  LuceneIndex index;

  @Override
  public int run() throws Exception {
    if (threads <= 0) {
      threads = 1;
    }

    dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    List<Change> allChangeList = allChanges();
    monitor.beginTask("Scanning changes", allChangeList.size());
    monitor.startWorkers(threads);
    for (int tid = 0; tid < threads; tid++) {
      new Worker(allChangeList).start();
    }
    monitor.waitForCompletion();
    monitor.endTask();
    manager.stop();
    return 0;
  }

  private List<Change> allChanges() throws OrmException {
    final ReviewDb db = database.open();
    try {
      return db.changes().all().toList();
    } finally {
      db.close();
    }
  }

  private class Worker extends Thread {
    private final List<Change> changes;

    Worker(List<Change> changes) {
      this.changes = changes;
    }

    @Override
    public void run() {
      try {
        UpdateTransaction txn = index.update();
        try {
          for (Change c : changes) {
            monitor.update(1);
            txn.add(c);
          }
          txn.commit();
        } finally {
          monitor.endWorker();
          txn.close();
        }
      } catch (IndexingException err) {
        err.printStackTrace();
        return;
      }
    }
  }
}
