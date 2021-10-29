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
import com.google.common.flogger.FluentLogger;
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
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
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

  private static class Reference<T> {
    public T value;
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

    public abstract Set<ChangeNotesResult> changes();

    public abstract int number();

    private static ProjectSlice create(Project.NameKey name, int number) {
      return new AutoValue_AllChangesIndexer_ProjectSlice(
          name, new HashSet<ChangeNotesResult>(), number);
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
    List<ProjectSlice> projectSlices;
    Stopwatch sw = Stopwatch.createStarted();
    try {
      projectSlices = new SliceCreator().create(projectCache);
    } catch (ProjectsCollectionFailure e) {
      logger.atSevere().log(e.getMessage());
      return Result.create(sw, false, 0, 0);
    }

    // projectSlices are currently grouped by projects. First all slices for project1, followed
    // by all slices for project2, and so on. As workers pick tasks sequentially, multiple threads
    // would typically work concurrently on different slices of the same project. While this is not
    // a big issue, shuffling the list beforehand helps with ungrouping the project slices, so
    // different slices are less likely to be worked on concurrently.
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

    for (ProjectSlice slice : projectSlices) {
      ListenableFuture<?> future =
          executor.submit(
              reindexProjectSlice(
                  indexerFactory.create(executor, index),
                  slice.name(),
                  slice.changes(),
                  doneTask,
                  failedTask));
      String description = "project " + slice.name() + " (slice-" + slice.number() + ")";
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
    }
    return Result.create(sw, ok.get(), nDone, nFailed);
  }

  public Callable<Void> reindexProject(
      ChangeIndexer indexer, Project.NameKey project, Task done, Task failed) {
    try (Repository repo = repoManager.openRepository(project)) {
      Set<ChangeNotesResult> changes =
          notesFactory.scan(repo, project, null).collect(Collectors.toSet());
      return reindexProjectSlice(indexer, project, changes, done, failed);
    } catch (IOException e) {
      logger.atSevere().log(e.getMessage());
      return null;
    }
  }

  public Callable<Void> reindexProjectSlice(
      ChangeIndexer indexer,
      Project.NameKey project,
      Set<ChangeNotesResult> changes,
      Task done,
      Task failed) {
    return new ProjectSliceIndexer(indexer, project, changes, done, failed);
  }

  private class ProjectSliceIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final Project.NameKey project;
    private final Set<ChangeNotesResult> changes;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;

    private ProjectSliceIndexer(
        ChangeIndexer indexer,
        Project.NameKey project,
        Set<ChangeNotesResult> changes,
        ProgressMonitor done,
        ProgressMonitor failed) {
      this.indexer = indexer;
      this.project = project;
      this.changes = changes;
      this.done = done;
      this.failed = failed;
    }

    @Override
    public Void call() throws Exception {
      OnlineReindexMode.begin();
      changes.forEach(r -> index(r));
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
    List<ProjectSlice> filledSlices = new ArrayList<>();
    Map<Project.NameKey, ProjectSlice> sliceByProject = new HashMap<>();
    int changeCount = 0;

    private List<ProjectSlice> create(ProjectCache projectCache) throws ProjectsCollectionFailure {
      ProgressMonitor pm = new TextProgressMonitor();
      pm.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
      int projectsCount = projectCache.all().size();
      int projectsFailed = 0;
      for (Project.NameKey project : projectCache.all()) {
        try {
          create(project);
        } catch (IOException e) {
          logger.atSevere().withCause(e).log("Error collecting project %s", project);
          projectsFailed++;
          if (projectsFailed > projectsCount / 2) {
            throw new ProjectsCollectionFailure(
                "Over 50%% of the projects could not be collected: aborted");
          }
        }
        pm.update(1);
      }
      pm.endTask();
      setTotalWork(changeCount);
      return filledSlices;
    }

    private void create(Project.NameKey project) throws IOException {
      Reference<Integer> sliceNumber = new Reference<>();
      sliceNumber.value = 0;
      try (Repository repo = repoManager.openRepository(project)) {
        notesFactory
            .scan(repo, project, null)
            .parallel()
            .collect(Collectors.toSet())
            .forEach(
                c -> {
                  ProjectSlice slice =
                      sliceByProject.computeIfAbsent(
                          project, s -> ProjectSlice.create(project, sliceNumber.value++));
                  slice.changes().add(c);
                  popAndQueueIfFull(slice);
                });
        popAndQueue(project);
      }
      if (sliceNumber.value > 0) {
        verboseWriter.println(
            "Submitting " + project + " for indexing in " + sliceNumber.value + " slices");
      }
    }

    private void popAndQueueIfFull(ProjectSlice slice) {
      popAndQueueIf(slice, s -> s.changes().size() == PROJECT_SLICE_MAX_REFS);
    }

    private void popAndQueue(Project.NameKey project) {
      ProjectSlice slice = sliceByProject.get(project);
      if (slice != null) {
        popAndQueueIf(slice, s -> s.changes().size() > 0);
      }
    }

    private void popAndQueueIf(ProjectSlice slice, Predicate<ProjectSlice> pred) {
      if (pred.test(slice)) {
        filledSlices.add(sliceByProject.remove(slice.name()));
        changeCount += slice.changes().size();
      }
    }
  }
}
