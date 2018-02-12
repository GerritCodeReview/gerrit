// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.notedb.ChangeBundle;
import com.google.gerrit.server.notedb.GwtormChangeBundleReader;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.kohsuke.args4j.Option;

public class H2CME extends SiteProgram {
  @Option(name = "--threads", usage = "Number of threads to use for reading")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--len", usage = "Number of tasks to submit per thread")
  private int len = 100;

  @Inject @ReviewDbFactory private SchemaFactory<ReviewDb> schemaFactory;
  @Inject private GwtormChangeBundleReader bundleReader;

  private Injector dbInjector;
  private LifecycleManager dbManager;

  @Override
  public int run() throws Exception {
    RuntimeShutdown.add(this::stop);
    try {
      mustHaveValidSite();
      dbInjector = createDbInjector(MULTI_USER);

      dbManager = new LifecycleManager();
      dbManager.add(dbInjector);
      dbManager.start();

      dbInjector.injectMembers(this);

      ImmutableList<Change.Id> changes = allChanges();
      checkState(changes.size() > 0, "site needs some changes");

      ExecutorService executor = Executors.newFixedThreadPool(2 * threads);

      // Start N threads spinning to take up CPU.
      for (int i = 0; i < threads; i++) {
        Thread t =
            new Thread(
                () -> {
                  for (int j = 0; ; j++) {
                    if (j % 10000000 == 1234) {
                      try {
                        Thread.sleep(1);
                      } catch (InterruptedException e) {
                        System.err.println("Spinner " + j + " interrupted");
                      }
                    }
                  }
                },
                "Spinner-" + i);
        t.setDaemon(true);
        t.start();
      }

      // Read lots of changes in parallel.
      AtomicInteger outputCounter = new AtomicInteger();
      try (ContextHelper contextHelper = new ContextHelper()) {
        for (int i = 0; i < len * threads; i++) {
          int idx = i;
          executor.submit(
              () -> {
                String origThreadName = Thread.currentThread().getName();
                try {
                  Change.Id id = changes.get(idx % changes.size());
                  Thread.currentThread().setName("Change-" + id);
                  ChangeBundle bundle = bundleReader.fromReviewDb(contextHelper.getReviewDb(), id);
                  int n =
                      bundle.getPatchSets().size()
                          + bundle.getChangeMessages().size()
                          + bundle.getPatchSetApprovals().size()
                          + bundle.getPatchLineComments().size()
                          + 1;
                  if (outputCounter.incrementAndGet() % 1000 == 0) {
                    System.err.println("Read " + n + " entities for change " + id);
                  }
                  return null;
                } finally {
                  Thread.currentThread().setName(origThreadName);
                }
              });
        }
      }
      executor.shutdown();
      executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    } finally {
      stop();
    }
    return 0;
  }

  private ImmutableList<Change.Id> allChanges() throws OrmException {
    try (ReviewDb db = schemaFactory.open()) {
      return Streams.stream(db.changes().all()).map(Change::getId).collect(toImmutableList());
    }
  }

  private void stop() {
    LifecycleManager m = dbManager;
    dbManager = null;
    if (m != null) {
      m.stop();
    }
  }

  private class ContextHelper implements AutoCloseable {
    private ReviewDb db;
    private Runnable closeDb;

    synchronized ReviewDb getReviewDb() throws OrmException {
      if (db == null) {
        ReviewDb actual = schemaFactory.open();
        closeDb = actual::close;
        db =
            new ReviewDbWrapper(actual) {
              @Override
              public void close() {
                // Closed by ContextHelper#close.
              }
            };
      }
      return db;
    }

    @Override
    public synchronized void close() {
      if (db != null) {
        closeDb.run();
        db = null;
        closeDb = null;
      }
    }
  }
}
