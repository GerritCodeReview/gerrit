// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.util.concurrent.Futures.successfulAsList;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.auto.value.AutoValue;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.MultiProgressMonitor.TaskKind;
import com.google.gerrit.server.git.MultiProgressMonitor.VolatileTask;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.OnlineReindexMode;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

/**
 * Implementation that can index all changes on a host or within a project. Used by Gerrit's
 * initialization and upgrade programs as well as by REST API endpoints that offer this
 * functionality.
 */
public class AllChangesIndexer extends SiteIndexer<Change.Id, ChangeData, ChangeIndex> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private MultiProgressMonitor mpm;
  private VolatileTask doneTask;
  private Task failedTask;
  private static final int PROJECT_SLICE_MAX_REFS = 1000;

  private final MultiProgressMonitor.Factory multiProgressMonitorFactory;

  private static class ProjectsCollectionFailure extends Exception {
    private static final long serialVersionUID = 1L;

    public ProjectsCollectionFailure(String message) {
      super(message);
    }
  }

  private final ChangeData.Factory changeDataFactory;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final ChangeIndexer.Factory indexerFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ProjectCache projectCache;

  @Inject
  AllChangesIndexer(
      MultiProgressMonitor.Factory multiProgressMonitorFactory,
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ChangeIndexer.Factory indexerFactory,
      ChangeNotes.Factory notesFactory,
      ProjectCache projectCache) {
    this.multiProgressMonitorFactory = multiProgressMonitorFactory;
    this.changeDataFactory = changeDataFactory;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexerFactory = indexerFactory;
    this.notesFactory = notesFactory;
    this.projectCache = projectCache;
  }

  @AutoValue
  public abstract static class ProjectSlice {
    public abstract Project.NameKey name();

    public abstract int slice();

    public abstract int slices();

    public abstract ImmutableMap<Change.Id, ObjectId> metaIdByChange();

    private static ProjectSlice create(
        Project.NameKey name,
        int slice,
        int slices,
        ImmutableMap<Change.Id, ObjectId> metaIdByChange) {
      return new AutoValue_AllChangesIndexer_ProjectSlice(name, slice, slices, metaIdByChange);
    }

    private static ProjectSlice oneSlice(
        Project.NameKey name, ImmutableMap<Change.Id, ObjectId> metaIdByChange) {
      return new AutoValue_AllChangesIndexer_ProjectSlice(name, 0, 1, metaIdByChange);
    }
  }

  @Override
  public Result indexAll(ChangeIndex index) {
    // The simplest approach to distribute indexing would be to let each thread grab a project
    // and index it fully. But if a site has one big project and 100s of small projects, then
    // in the beginning all CPUs would be busy reindexing projects. But soon enough all small
    // projects have been reindexed, and only the thread that reindexes the big project is
    // still working. The other threads would idle. Reindexing the big project on a single
    // thread becomes the critical path. Bringing in more CPUs would not speed up things.
    //
    // To avoid such situations, we split big repos into smaller parts and let
    // the thread pool index these smaller parts. This splitting introduces an overhead in the
    // workload setup and there might be additional slow-downs from multiple threads
    // concurrently working on different parts of the same project. But for Wikimedia's Gerrit,
    // which had 2 big projects, many middle sized ones, and lots of smaller ones, the
    // splitting of repos into smaller parts reduced indexing time from 1.5 hours to 55 minutes
    // in 2020.

    Stopwatch sw = Stopwatch.createStarted();
    AtomicBoolean ok = new AtomicBoolean(true);
    mpm = multiProgressMonitorFactory.create(progressOut, TaskKind.INDEXING, "Reindexing changes");
    doneTask = mpm.beginVolatileSubTask("changes");
    failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);
    List<ListenableFuture<?>> futures;
    try {
      futures = new SliceScheduler(index, ok).schedule();
    } catch (ProjectsCollectionFailure e) {
      logger.atSevere().log("%s", e.getMessage());
      return Result.create(sw, false, 0, 0);
    }

    try {
      mpm.waitFor(
          transform(
              successfulAsList(futures),
              x -> {
                mpm.end();
                return null;
              },
              directExecutor()));
    } catch (UncheckedExecutionException e) {
      logger.atSevere().withCause(e).log("Error in batch indexer");
      ok.set(false);
    }
    // If too many changes failed, maybe there was a bug in the indexer. Don't
    // trust the results. This is not an exact percentage since we bump the same
    // failure counter if a project can't be read, but close enough.
    int nFailed = failedTask.getCount();
    int nDone = doneTask.getCount();
    int nTotal = nFailed + nDone;
    double pctFailed = ((double) nFailed) / nTotal * 100;
    if (pctFailed > 10) {
      logger.atSevere().log(
          "Failed %s/%s changes (%s%%); not marking new index as ready",
          nFailed, nTotal, Math.round(pctFailed));
      ok.set(false);
    } else if (nFailed > 0) {
      logger.atWarning().log("Failed %s/%s changes", nFailed, nTotal);
    }
    return Result.create(sw, ok.get(), nDone, nFailed);
  }

  @Nullable
  public Callable<Void> reindexProject(
      ChangeIndexer indexer, Project.NameKey project, Task done, Task failed) {
    try (Repository repo = repoManager.openRepository(project)) {
      return reindexProjectSlice(
          indexer,
          ProjectSlice.oneSlice(project, ChangeNotes.Factory.scanChangeIds(repo)),
          done,
          failed);
    } catch (IOException e) {
      logger.atSevere().log("%s", e.getMessage());
      return null;
    }
  }

  public Callable<Void> reindexProjectSlice(
      ChangeIndexer indexer, ProjectSlice projectSlice, Task done, Task failed) {
    return new ProjectSliceIndexer(indexer, projectSlice, done, failed);
  }

  private class ProjectSliceIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final ProjectSlice projectSlice;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;

    private ProjectSliceIndexer(
        ChangeIndexer indexer,
        ProjectSlice projectSlice,
        ProgressMonitor done,
        ProgressMonitor failed) {
      this.indexer = indexer;
      this.projectSlice = projectSlice;
      this.done = done;
      this.failed = failed;
    }

    @Override
    public Void call() throws Exception {
      OnlineReindexMode.begin();
      // Order of scanning changes is undefined. This is ok if we assume that packfile locality is
      // not important for indexing, since sites should have a fully populated DiffSummary cache.
      // It does mean that reindexing after invalidating the DiffSummary cache will be expensive,
      // but the goal is to invalidate that cache as infrequently as we possibly can. And besides,
      // we don't have concrete proof that improving packfile locality would help.
      notesFactory
          .scan(
              projectSlice.metaIdByChange(),
              projectSlice.name(),
              id -> (id.get() % projectSlice.slices()) == projectSlice.slice())
          .forEach(r -> index(r));
      OnlineReindexMode.end();
      return null;
    }

    private void index(ChangeNotesResult r) {
      if (r.error().isPresent()) {
        fail("Failed to read change " + r.id() + " for indexing", true, r.error().get());
        return;
      }
      try {
        indexer.index(changeDataFactory.create(r.notes()));
        done.update(1);
        verboseWriter.format(
            "Reindexed change %d (project: %s)\n", r.id().get(), r.notes().getProjectName().get());
      } catch (RejectedExecutionException e) {
        // Server shutdown, don't spam the logs.
        failSilently();
      } catch (Exception e) {
        fail("Failed to index change " + r.id(), true, e);
      }
    }

    private void fail(String error, boolean failed, Throwable e) {
      if (failed) {
        this.failed.update(1);
      }

      logger.atWarning().withCause(e).log("%s", error);
      verboseWriter.println(error);
    }

    private void failSilently() {
      this.failed.update(1);
    }

    @Override
    public String toString() {
      if (projectSlice.slices() == 1) {
        return "Index all changes of project " + projectSlice.name();
      }
      return "Index changes slice "
          + projectSlice.slice()
          + "/"
          + projectSlice.slices()
          + " of project "
          + projectSlice.name();
    }
  }

  private class SliceScheduler {
    final ChangeIndex index;
    final AtomicBoolean ok;
    final AtomicInteger changeCount = new AtomicInteger(0);
    final AtomicInteger projectsFailed = new AtomicInteger(0);
    final List<ListenableFuture<?>> sliceIndexerFutures = new ArrayList<>();
    final List<ListenableFuture<?>> sliceCreationFutures = new ArrayList<>();
    VolatileTask projTask = mpm.beginVolatileSubTask("project-slices");
    Task slicingProjects;

    public SliceScheduler(ChangeIndex index, AtomicBoolean ok) {
      this.index = index;
      this.ok = ok;
    }

    private List<ListenableFuture<?>> schedule() throws ProjectsCollectionFailure {
      ImmutableSortedSet<Project.NameKey> projects = projectCache.all();
      int projectCount = projects.size();
      slicingProjects = mpm.beginSubTask("Slicing projects", projectCount);
      for (Project.NameKey name : projects) {
        sliceCreationFutures.add(executor.submit(new ProjectSliceCreator(name)));
      }

      try {
        mpm.waitForNonFinalTask(
            transform(
                successfulAsList(sliceCreationFutures),
                x -> {
                  projTask.finalizeTotal();
                  doneTask.finalizeTotal();
                  return null;
                },
                directExecutor()));
      } catch (UncheckedExecutionException e) {
        logger.atSevere().withCause(e).log("Error project slice creation");
        ok.set(false);
      }

      if (projectsFailed.get() > projectCount / 2) {
        throw new ProjectsCollectionFailure(
            "Over 50%% of the projects could not be collected: aborted");
      }

      slicingProjects.endTask();
      setTotalWork(changeCount.get());

      return sliceIndexerFutures;
    }

    private class ProjectSliceCreator implements Callable<Void> {
      final Project.NameKey name;

      public ProjectSliceCreator(Project.NameKey name) {
        this.name = name;
      }

      @Override
      public Void call() throws IOException {
        try (Repository repo = repoManager.openRepository(name)) {
          ImmutableMap<Change.Id, ObjectId> metaIdByChange =
              ChangeNotes.Factory.scanChangeIds(repo);
          int size = metaIdByChange.size();
          if (size > 0) {
            changeCount.addAndGet(size);
            int slices = 1 + (size - 1) / PROJECT_SLICE_MAX_REFS;
            if (slices > 1) {
              verboseWriter.println(
                  "Submitting " + name + " for indexing in " + slices + " slices");
            }

            doneTask.updateTotal(size);
            projTask.updateTotal(slices);

            for (int slice = 0; slice < slices; slice++) {
              ProjectSlice projectSlice = ProjectSlice.create(name, slice, slices, metaIdByChange);
              ListenableFuture<?> future =
                  executor.submit(
                      reindexProjectSlice(
                          indexerFactory.create(executor, index),
                          projectSlice,
                          doneTask,
                          failedTask));
              String description = "project " + name + " (" + slice + "/" + slices + ")";
              addErrorListener(future, description, projTask, ok);
              sliceIndexerFutures.add(future);
            }
          }
        } catch (IOException e) {
          logger.atSevere().withCause(e).log("Error collecting project %s", name);
          projectsFailed.incrementAndGet();
        }
        slicingProjects.update(1);
        return null;
      }
    }
  }
}
