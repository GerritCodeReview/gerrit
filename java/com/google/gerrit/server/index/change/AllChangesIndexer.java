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

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.Futures.successfulAsList;
import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.auto.value.AutoValue;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.OnlineReindexMode;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ScanResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/**
 * Implementation that can index all changes on a host or within a project. Used by Gerrit's
 * initialization and upgrade programs as well as by REST API endpoints that offer this
 * functionality.
 */
public class AllChangesIndexer extends SiteIndexer<Change.Id, ChangeData, ChangeIndex> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int PROJECT_SLICE_MAX_REFS = 1000;

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
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ChangeIndexer.Factory indexerFactory,
      ChangeNotes.Factory notesFactory,
      ProjectCache projectCache) {
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

    public abstract ScanResult scanResult();

    private static ProjectSlice create(Project.NameKey name, int slice, int slices, ScanResult sr) {
      return new AutoValue_AllChangesIndexer_ProjectSlice(name, slice, slices, sr);
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
    List<ProjectSlice> projectSlices;
    try {
      projectSlices = new SliceCreator().create();
    } catch (ProjectsCollectionFailure | InterruptedException | ExecutionException e) {
      logger.atSevere().log(e.getMessage());
      return Result.create(sw, false, 0, 0);
    }

    // Since project slices are created in parallel, they are somewhat shuffled already. However,
    // the number of threads used to create the project slices doesn't guarantee good randomization.
    // If the slices are not shuffled well, then multiple threads would typically work concurrently
    // on different slices of the same project. While this is not a big issue, shuffling the list
    // beforehand helps with ungrouping the project slices, so different slices are less likely to
    // be worked on concurrently.
    // This shuffling gave a 6% runtime reduction for Wikimedia's Gerrit in 2020.
    Collections.shuffle(projectSlices);
    return indexAll(index, projectSlices);
  }

  private SiteIndexer.Result indexAll(ChangeIndex index, List<ProjectSlice> projectSlices) {
    Stopwatch sw = Stopwatch.createStarted();
    MultiProgressMonitor mpm = new MultiProgressMonitor(progressOut, "Reindexing changes");
    Task projTask = mpm.beginSubTask("project-slices", projectSlices.size());
    checkState(totalWork >= 0);
    Task doneTask = mpm.beginSubTask(null, totalWork);
    Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    List<ListenableFuture<?>> futures = new ArrayList<>();
    AtomicBoolean ok = new AtomicBoolean(true);

    for (ProjectSlice projectSlice : projectSlices) {
      Project.NameKey name = projectSlice.name();
      int slice = projectSlice.slice();
      int slices = projectSlice.slices();
      ListenableFuture<?> future =
          executor.submit(
              reindexProject(
                  indexerFactory.create(executor, index),
                  name,
                  slice,
                  slices,
                  projectSlice.scanResult(),
                  doneTask,
                  failedTask));
      String description = "project " + name + " (" + slice + "/" + slices + ")";
      addErrorListener(future, description, projTask, ok);
      futures.add(future);
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

  public Callable<Void> reindexProject(
      ChangeIndexer indexer, Project.NameKey project, Task done, Task failed) {
    try (Repository repo = repoManager.openRepository(project)) {
      return reindexProject(
          indexer, project, 0, 1, ChangeNotes.Factory.scanChangeIds(repo), done, failed);
    } catch (IOException e) {
      logger.atSevere().log(e.getMessage());
      return null;
    }
  }

  public Callable<Void> reindexProject(
      ChangeIndexer indexer,
      Project.NameKey project,
      int slice,
      int slices,
      ScanResult scanResult,
      Task done,
      Task failed) {
    return new ProjectIndexer(indexer, project, slice, slices, scanResult, done, failed);
  }

  private class ProjectIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final Project.NameKey project;
    private final int slice;
    private final int slices;
    private final ScanResult scanResult;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;

    private ProjectIndexer(
        ChangeIndexer indexer,
        Project.NameKey project,
        int slice,
        int slices,
        ScanResult scanResult,
        ProgressMonitor done,
        ProgressMonitor failed) {
      this.indexer = indexer;
      this.project = project;
      this.slice = slice;
      this.slices = slices;
      this.scanResult = scanResult;
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
          .scan(scanResult, project, id -> (id.get() % slices) == slice)
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

      logger.atWarning().withCause(e).log(error);
      verboseWriter.println(error);
    }

    private void failSilently() {
      this.failed.update(1);
    }

    @Override
    public String toString() {
      return "Index all changes of project " + project.get();
    }
  }

  private class SliceCreator {
    final Set<ProjectSlice> projectSlices = Sets.newConcurrentHashSet();
    final AtomicInteger changeCount = new AtomicInteger(0);
    final AtomicInteger projectsFailed = new AtomicInteger(0);
    final ProgressMonitor pm = new TextProgressMonitor();

    private List<ProjectSlice> create()
        throws ProjectsCollectionFailure, InterruptedException, ExecutionException {
      List<ListenableFuture<?>> futures = new ArrayList<>();
      pm.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
      for (Project.NameKey name : projectCache.all()) {
        futures.add(executor.submit(new ProjectSliceCreator(name)));
      }

      Futures.allAsList(futures).get();

      if (projectsFailed.get() > projectCache.all().size() / 2) {
        throw new ProjectsCollectionFailure(
            "Over 50%% of the projects could not be collected: aborted");
      }

      pm.endTask();
      setTotalWork(changeCount.get());
      return projectSlices.stream().collect(Collectors.toList());
    }

    private class ProjectSliceCreator implements Callable<Void> {
      final Project.NameKey name;

      public ProjectSliceCreator(Project.NameKey name) {
        this.name = name;
      }

      @Override
      public Void call() throws IOException {
        try (Repository repo = repoManager.openRepository(name)) {
          ScanResult sr = ChangeNotes.Factory.scanChangeIds(repo);
          int size = sr.all().size();
          if (size > 0) {
            changeCount.addAndGet(size);
            int slices = 1 + size / PROJECT_SLICE_MAX_REFS;
            if (slices > 1) {
              verboseWriter.println(
                  "Submitting " + name + " for indexing in " + slices + " slices");
            }
            for (int slice = 0; slice < slices; slice++) {
              projectSlices.add(ProjectSlice.create(name, slice, slices, sr));
            }
          }
        } catch (IOException e) {
          logger.atSevere().withCause(e).log("Error collecting project %s", name);
          projectsFailed.incrementAndGet();
        }
        pm.update(1);
        return null;
      }
    }
  }
}
