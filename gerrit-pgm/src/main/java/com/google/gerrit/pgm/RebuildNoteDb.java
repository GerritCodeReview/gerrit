// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.change.ReindexAfterUpdate;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.schema.DisabledChangesReviewDbWrapper;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RebuildNoteDb extends SiteProgram {
  private static final Logger log =
      LoggerFactory.getLogger(RebuildNoteDb.class);

  @Option(name = "--threads",
      usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--project",
      usage = "Projects to rebuild; recommended for debugging only")
  private List<String> projects = new ArrayList<>();

  @Option(name = "--change",
      usage = "Individual change numbers to rebuild; recommended for debugging only")
  private List<Integer> changes = new ArrayList<>();

  private Injector dbInjector;
  private Injector sysInjector;

  @Inject
  private AllUsersName allUsersName;

  @Inject
  private ChangeRebuilder rebuilder;

  @Inject
  @GerritServerConfig
  private Config cfg;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private NotesMigration notesMigration;

  @Inject
  private SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  private WorkQueue workQueue;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
    if (!notesMigration.enabled()) {
      throw die("NoteDb is not enabled.");
    }
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    System.out.println("Rebuilding the NoteDb");

    ImmutableMultimap<Project.NameKey, Change.Id> changesByProject =
        getChangesByProject();
    boolean ok = true;
    Stopwatch sw = Stopwatch.createStarted();
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName);
        ReviewDb db = unwrap(schemaFactory.open())) {
      deleteRefs(RefNames.REFS_DRAFT_COMMENTS, allUsersRepo);
      List<Project.NameKey> projectNames = Ordering.usingToString()
          .sortedCopy(changesByProject.keySet());
      for (Project.NameKey project : projectNames) {
        try {
          ok |= rebuilder.rebuildProject(db, changesByProject, project, allUsersRepo);
        } catch (Exception e) {
          log.error("Error rebuilding project " + project, e);
          ok = false;
        }
      }
    }

    double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Rebuild %d changes in %.01fs (%.01f/s)\n",
        changesByProject.size(), t, changesByProject.size() / t);
    return ok ? 0 : 1;
  }

  private static void execute(BatchRefUpdate bru, Repository repo)
      throws IOException {
    try (RevWalk rw = new RevWalk(repo)) {
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    }
    for (ReceiveCommand command : bru.getCommands()) {
      if (command.getResult() != ReceiveCommand.Result.OK) {
        throw new IOException(String.format("Command %s failed: %s",
            command.toString(), command.getResult()));
      }
    }
  }

  private void deleteRefs(String prefix, Repository allUsersRepo)
      throws IOException {
    RefDatabase refDb = allUsersRepo.getRefDatabase();
    Map<String, Ref> allRefs = refDb.getRefs(prefix);
    BatchRefUpdate bru = refDb.newBatchUpdate();
    for (Map.Entry<String, Ref> ref : allRefs.entrySet()) {
      bru.addCommand(new ReceiveCommand(ref.getValue().getObjectId(),
          ObjectId.zeroId(), prefix + ref.getKey()));
    }
    execute(bru, allUsersRepo);
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(new AbstractModule() {
      @Override
      public void configure() {
        install(dbInjector.getInstance(BatchProgramModule.class));
        install(SearchingChangeCacheImpl.module());
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(
            ReindexAfterUpdate.class);
        install(new DummyIndexModule());
      }
    });
  }

  private ListeningExecutorService newExecutor() {
    if (threads > 0) {
      return MoreExecutors.listeningDecorator(
          workQueue.createQueue(threads, "RebuildChange"));
    } else {
      return MoreExecutors.newDirectExecutorService();
    }
  }

  private ImmutableMultimap<Project.NameKey, Change.Id> getChangesByProject()
      throws OrmException {
    // Memorize all changes so we can close the db connection and allow
    // rebuilder threads to use the full connection pool.
    Multimap<Project.NameKey, Change.Id> changesByProject =
        ArrayListMultimap.create();
    try (ReviewDb db = schemaFactory.open()) {
      if (projects.isEmpty() && !changes.isEmpty()) {
        Iterable<Change> todo = unwrap(db).changes().get(
            Iterables.transform(changes, new Function<Integer, Change.Id>() {
              @Override
              public Change.Id apply(Integer in) {
                return new Change.Id(in);
              }
            }));
        for (Change c : todo) {
          changesByProject.put(c.getProject(), c.getId());
        }
      } else {
        for (Change c : unwrap(db).changes().all()) {
          boolean include = false;
          if (projects.isEmpty() && changes.isEmpty()) {
            include = true;
          } else if (!projects.isEmpty()
              && projects.contains(c.getProject().get())) {
            include = true;
          } else if (!changes.isEmpty() && changes.contains(c.getId().get())) {
            include = true;
          }
          if (include) {
            changesByProject.put(c.getProject(), c.getId());
          }
        }
      }
      return ImmutableMultimap.copyOf(changesByProject);
    }
  }

  private static ReviewDb unwrap(ReviewDb db) {
    if (db instanceof DisabledChangesReviewDbWrapper) {
      db = ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }

  private static class RebuildListener implements Runnable {
    private Change.Id changeId;
    private ListenableFuture<?> future;
    private AtomicBoolean ok;
    private Task doneTask;
    private Task failedTask;

    private RebuildListener(Change.Id changeId, ListenableFuture<?> future,
        AtomicBoolean ok, Task doneTask, Task failedTask) {
      this.changeId = changeId;
      this.future = future;
      this.ok = ok;
      this.doneTask = doneTask;
      this.failedTask = failedTask;
    }

    @Override
    public void run() {
      try {
        future.get();
        doneTask.update(1);
      } catch (ExecutionException | InterruptedException e) {
        if (e.getCause() instanceof RepositoryNotFoundException) {
          noRepo((RepositoryNotFoundException) e.getCause());
        } else {
          fail(e);
        }
      } catch (RuntimeException e) {
        failAndThrow(e);
      } catch (Error e) {
        // Can't join with RuntimeException because "RuntimeException
        // | Error" becomes Throwable, which messes with signatures.
        failAndThrow(e);
      }
    }

    private void fail(Throwable t) {
      log.error("Failed to rebuild change " + changeId, t);
      ok.set(false);
      failedTask.update(1);
    }

    private void noRepo(RepositoryNotFoundException e) {
      log.warn("Skipped rebuilding change " + changeId + ": " + e.getMessage());
      // Don't flip ok bit.
      failedTask.update(1);
    }

    private void failAndThrow(RuntimeException e) {
      fail(e);
      throw e;
    }

    private void failAndThrow(Error e) {
      fail(e);
      throw e;
    }
  }
}
