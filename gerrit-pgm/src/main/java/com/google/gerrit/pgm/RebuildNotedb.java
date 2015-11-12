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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.FormatUtil;
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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.ReindexAfterUpdate;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.BatchRefUpdate;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RebuildNotedb extends SiteProgram {
  private static final Logger log =
      LoggerFactory.getLogger(RebuildNotedb.class);

  @Option(name = "--threads",
      usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    NotesMigration notesMigration = sysInjector.getInstance(
        NotesMigration.class);
    if (!notesMigration.enabled()) {
      die("Notedb is not enabled.");
    }
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    ListeningExecutorService executor = newExecutor();
    System.out.println("Rebuilding the notedb");
    ChangeRebuilder rebuilder = sysInjector.getInstance(ChangeRebuilder.class);

    Multimap<Project.NameKey, Change> changesByProject = getChangesByProject();
    final AtomicBoolean ok = new AtomicBoolean(true);
    Stopwatch sw = Stopwatch.createStarted();
    GitRepositoryManager repoManager =
        sysInjector.getInstance(GitRepositoryManager.class);
    final Project.NameKey allUsersName =
        sysInjector.getInstance(AllUsersName.class);
    try (Repository allUsersRepo =
        repoManager.openMetadataRepository(allUsersName)) {
      deleteRefs(RefNames.REFS_DRAFT_COMMENTS, allUsersRepo);
      deleteRefs(RefNames.REFS_STARRED_CHANGES, allUsersRepo);
      for (final Project.NameKey project : changesByProject.keySet()) {
        try (Repository repo = repoManager.openMetadataRepository(project)) {
          final BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
          final BatchRefUpdate bruAllUsers =
              allUsersRepo.getRefDatabase().newBatchUpdate();
          List<ListenableFuture<?>> futures = Lists.newArrayList();

          // Here, we elide the project name to 50 characters to ensure that
          // the whole monitor line for a project fits on one line (<80 chars).
          final MultiProgressMonitor mpm = new MultiProgressMonitor(System.out,
              FormatUtil.elide(project.get(), 50));
          final Task doneTask =
              mpm.beginSubTask("done", changesByProject.get(project).size());
          final Task failedTask =
              mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

          for (final Change c : changesByProject.get(project)) {
            final ListenableFuture<?> future = rebuilder.rebuildAsync(c,
                executor, bru, bruAllUsers, repo, allUsersRepo);
            futures.add(future);
            future.addListener(
                new RebuildListener(c.getId(), future, ok, doneTask, failedTask),
                MoreExecutors.directExecutor());
          }

          mpm.waitFor(Futures.transformAsync(Futures.successfulAsList(futures),
              new AsyncFunction<List<?>, Void>() {
                  @Override
                public ListenableFuture<Void> apply(List<?> input)
                    throws Exception {
                  execute(bru, repo);
                  execute(bruAllUsers, allUsersRepo);
                  mpm.end();
                  return Futures.immediateFuture(null);
                }
              }));
        } catch (Exception e) {
          log.error("Error rebuilding notedb", e);
          ok.set(false);
          break;
        }
      }
    }

    double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Rebuild %d changes in %.01fs (%.01f/s)\n",
        changesByProject.size(), t, changesByProject.size() / t);
    return ok.get() ? 0 : 1;
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
        install(new NoteDbModule());
        DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(
            ReindexAfterUpdate.class);
        install(new DummyIndexModule());
      }
    });
  }

  private ListeningExecutorService newExecutor() {
    if (threads > 0) {
      return MoreExecutors.listeningDecorator(
          dbInjector.getInstance(WorkQueue.class)
            .createQueue(threads, "RebuildChange"));
    } else {
      return MoreExecutors.newDirectExecutorService();
    }
  }

  private Multimap<Project.NameKey, Change> getChangesByProject()
      throws OrmException {
    // Memorize all changes so we can close the db connection and allow
    // rebuilder threads to use the full connection pool.
    SchemaFactory<ReviewDb> schemaFactory = sysInjector.getInstance(Key.get(
        new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
    Multimap<Project.NameKey, Change> changesByProject =
        ArrayListMultimap.create();
    try (ReviewDb db = schemaFactory.open()) {
      for (Change c : db.changes().all()) {
        changesByProject.put(c.getProject(), c);
      }
      return changesByProject;
    }
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
        fail(e);
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
