// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Scan changes and update the trackingid information for them. */
public class ScanTrackingIds extends SiteProgram {
  @Option(name = "--threads", usage = "Number of concurrent threads to run")
  private int threads = 2 * Runtime.getRuntime().availableProcessors();

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();
  private List<Change> todo;

  private Injector dbInjector;

  @Inject
  private TrackingFooters footers;

  @Inject
  private GitRepositoryManager gitManager;

  @Inject
  private SchemaFactory<ReviewDb> database;

  @Override
  public int run() throws Exception {
    if (threads <= 0) {
      threads = 1;
    }

    dbInjector = createDbInjector(MULTI_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    final ReviewDb db = database.open();
    try {
      todo = db.changes().all().toList();
      synchronized (monitor) {
        monitor.beginTask("Scanning changes", todo.size());
      }
    } finally {
      db.close();
    }

    final List<Worker> workers = new ArrayList<Worker>(threads);
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

  private void scan(ReviewDb db, Change change) {
    final Project.NameKey project = change.getDest().getParentKey();
    final Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      return;
    }
    try {
      PatchSet ps = db.patchSets().get(change.currentPatchSetId());
      if (ps == null || ps.getRevision() == null
          || ps.getRevision().get() == null) {
        return;
      }
      ChangeUtil.updateTrackingIds(db, change, footers, parse(git, ps)
          .getFooterLines());
    } catch (OrmException error) {
      System.err.println("ERR " + error.getMessage());
    } catch (IOException error) {
      System.err.println("ERR Cannot scan " + change.getId() + ": "
          + error.getMessage());
    } finally {
      git.close();
    }
  }

  private RevCommit parse(final Repository git, PatchSet ps)
      throws MissingObjectException, IncorrectObjectTypeException, IOException {
    RevWalk rw = new RevWalk(git);
    try {
      return rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
    } finally {
      rw.release();
    }
  }

  private Change next() {
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
      ReviewDb db;
      try {
        db = database.open();
      } catch (OrmException e) {
        e.printStackTrace();
        return;
      }
      try {
        for (;;) {
          Change change = next();
          if (change == null) {
            break;
          }
          scan(db, change);
          synchronized (monitor) {
            monitor.update(1);
          }
        }
      } finally {
        db.close();
      }
    }
  }
}
