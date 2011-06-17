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

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.cache.CachePool;
import com.google.gerrit.server.config.ApprovalTypesProvider;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.git.CodeReviewNoteCreationException;
import com.google.gerrit.server.git.CreateCodeReviewNotes;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Scopes;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.lib.ThreadSafeProgressMonitor;
import org.eclipse.jgit.util.BlockList;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/** Export review notes for all submitted changes in all projects. */
public class ExportReviewNotes extends SiteProgram {
  @Option(name = "--threads", usage = "Number of concurrent threads to run")
  private int threads = 2;

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor textMonitor = new TextProgressMonitor();
  private final ThreadSafeProgressMonitor monitor =
      new ThreadSafeProgressMonitor(textMonitor);

  private Injector dbInjector;
  private Injector gitInjector;

  @Inject
  private GitRepositoryManager gitManager;

  @Inject
  private SchemaFactory<ReviewDb> database;

  @Inject
  private CreateCodeReviewNotes.Factory codeReviewNotesFactory;

  private Map<Project.NameKey, List<Change>> changes;

  @Override
  public int run() throws Exception {
    if (threads <= 0) {
      threads = 1;
    }

    dbInjector = createDbInjector(MULTI_USER);
    gitInjector = dbInjector.createChildInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(SchemaVersionCheck.module());
        bind(ApprovalTypes.class).toProvider(ApprovalTypesProvider.class).in(
            Scopes.SINGLETON);
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toProvider(CanonicalWebUrlProvider.class).in(Scopes.SINGLETON);
        bind(CachePool.class);

        install(AccountCacheImpl.module());
        install(GroupCacheImpl.module());
        install(new FactoryModule() {
          @Override
          protected void configure() {
            factory(CreateCodeReviewNotes.Factory.class);
          }
        });
        install(new LifecycleModule() {
          @Override
          protected void configure() {
            listener().to(CachePool.Lifecycle.class);
            listener().to(LocalDiskRepositoryManager.Lifecycle.class);
          }
        });
      }
    });

    manager.add(dbInjector, gitInjector);
    manager.start();
    gitInjector.injectMembers(this);

    List<Change> allChangeList = allChanges();
    monitor.beginTask("Scanning changes", allChangeList.size());
    changes = cluster(allChangeList);
    allChangeList = null;

    monitor.startWorkers(threads);
    for (int tid = 0; tid < threads; tid++) {
      new Worker().start();
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

  private Map<Project.NameKey, List<Change>> cluster(List<Change> changes) {
    HashMap<Project.NameKey, List<Change>> m =
        new HashMap<Project.NameKey, List<Change>>();
    for (Change change : changes) {
      if (change.getStatus() == Change.Status.MERGED) {
        List<Change> l = m.get(change.getProject());
        if (l == null) {
          l = new BlockList<Change>();
          m.put(change.getProject(), l);
        }
        l.add(change);
      } else {
        monitor.update(1);
      }
    }
    return m;
  }

  private void export(ReviewDb db, Project.NameKey project, List<Change> changes)
      throws IOException, OrmException, CodeReviewNoteCreationException,
      InterruptedException {
    final Repository git;
    try {
      git = gitManager.openRepository(project);
    } catch (RepositoryNotFoundException e) {
      return;
    }
    try {
      CreateCodeReviewNotes notes = codeReviewNotesFactory.create(db, git);
      try {
        notes.loadBase();
        for (Change change : changes) {
          monitor.update(1);
          PatchSet ps = db.patchSets().get(change.currentPatchSetId());
          if (ps == null) {
            continue;
          }
          notes.add(change, ObjectId.fromString(ps.getRevision().get()));
        }
        notes.commit("Exported prior reviews from Gerrit Code Review\n");
        notes.updateRef();
      } finally {
        notes.release();
      }
    } finally {
      git.close();
    }
  }

  private Map.Entry<Project.NameKey, List<Change>> next() {
    synchronized (changes) {
      if (changes.isEmpty()) {
        return null;
      }

      final Project.NameKey name = changes.keySet().iterator().next();
      final List<Change> list = changes.remove(name);
      return new Map.Entry<Project.NameKey, List<Change>>() {
        @Override
        public Project.NameKey getKey() {
          return name;
        }

        @Override
        public List<Change> getValue() {
          return list;
        }

        @Override
        public List<Change> setValue(List<Change> value) {
          throw new UnsupportedOperationException();
        }
      };
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
          Entry<Project.NameKey, List<Change>> next = next();
          if (next != null) {
            try {
              export(db, next.getKey(), next.getValue());
            } catch (IOException e) {
              e.printStackTrace();
            } catch (OrmException e) {
              e.printStackTrace();
            } catch (CodeReviewNoteCreationException e) {
              e.printStackTrace();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          } else {
            break;
          }
        }
      } finally {
        monitor.endWorker();
        db.close();
      }
    }
  }
}
