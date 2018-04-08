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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ComparisonChain;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.git.MultiProgressMonitor;
import com.google.gerrit.git.MultiProgressMonitor.Task;
import com.google.gerrit.index.SiteIndexer;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AllChangesIndexer extends SiteIndexer<Change.Id, ChangeData, ChangeIndex> {
  private static final Logger log = LoggerFactory.getLogger(AllChangesIndexer.class);

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final ChangeData.Factory changeDataFactory;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final ChangeIndexer.Factory indexerFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ProjectCache projectCache;

  @Inject
  AllChangesIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ChangeIndexer.Factory indexerFactory,
      ChangeNotes.Factory notesFactory,
      ProjectCache projectCache) {
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexerFactory = indexerFactory;
    this.notesFactory = notesFactory;
    this.projectCache = projectCache;
  }

  private static class ProjectHolder implements Comparable<ProjectHolder> {
    final Project.NameKey name;
    private final long size;

    ProjectHolder(Project.NameKey name, long size) {
      this.name = name;
      this.size = size;
    }

    @Override
    public int compareTo(ProjectHolder other) {
      // Sort projects based on size first to maximize utilization of threads early on.
      return ComparisonChain.start()
          .compare(other.size, size)
          .compare(other.name.get(), name.get())
          .result();
    }
  }

  @Override
  public Result indexAll(ChangeIndex index) {
    ProgressMonitor pm = new TextProgressMonitor();
    pm.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
    SortedSet<ProjectHolder> projects = new TreeSet<>();
    int changeCount = 0;
    Stopwatch sw = Stopwatch.createStarted();
    int projectsFailed = 0;
    for (Project.NameKey name : projectCache.all()) {
      try (Repository repo = repoManager.openRepository(name)) {
        long size = estimateSize(repo);
        changeCount += size;
        projects.add(new ProjectHolder(name, size));
      } catch (IOException e) {
        log.error("Error collecting project {}", name, e);
        projectsFailed++;
        if (projectsFailed > projects.size() / 2) {
          log.error("Over 50% of the projects could not be collected: aborted");
          return new Result(sw, false, 0, 0);
        }
      }
      pm.update(1);
    }
    pm.endTask();
    setTotalWork(changeCount);
    return indexAll(index, projects);
  }

  private long estimateSize(Repository repo) throws IOException {
    // Estimate size based on IDs that show up in ref names. This is not perfect, since patch set
    // refs may exist for changes whose metadata was never successfully stored. But that's ok, as
    // the estimate is just used as a heuristic for sorting projects.
    return repo.getRefDatabase()
        .getRefs(RefNames.REFS_CHANGES)
        .values()
        .stream()
        .map(r -> Change.Id.fromRef(r.getName()))
        .filter(Objects::nonNull)
        .distinct()
        .count();
  }

  private SiteIndexer.Result indexAll(ChangeIndex index, SortedSet<ProjectHolder> projects) {
    Stopwatch sw = Stopwatch.createStarted();
    MultiProgressMonitor mpm = new MultiProgressMonitor(progressOut, "Reindexing changes");
    Task projTask = mpm.beginSubTask("projects", projects.size());
    checkState(totalWork >= 0);
    Task doneTask = mpm.beginSubTask(null, totalWork);
    Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    List<ListenableFuture<?>> futures = new ArrayList<>();
    AtomicBoolean ok = new AtomicBoolean(true);

    for (ProjectHolder project : projects) {
      ListenableFuture<?> future =
          executor.submit(
              reindexProject(
                  indexerFactory.create(executor, index), project.name, doneTask, failedTask));
      addErrorListener(future, "project " + project.name, projTask, ok);
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
    } catch (ExecutionException e) {
      log.error("Error in batch indexer", e);
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
      log.error(
          "Failed {}/{} changes ({}%); not marking new index as ready",
          nFailed, nTotal, Math.round(pctFailed));
      ok.set(false);
    }
    return new Result(sw, ok.get(), nDone, nFailed);
  }

  public Callable<Void> reindexProject(
      ChangeIndexer indexer, Project.NameKey project, Task done, Task failed) {
    return new ProjectIndexer(indexer, project, done, failed);
  }

  private class ProjectIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final Project.NameKey project;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;

    private ProjectIndexer(
        ChangeIndexer indexer,
        Project.NameKey project,
        ProgressMonitor done,
        ProgressMonitor failed) {
      this.indexer = indexer;
      this.project = project;
      this.done = done;
      this.failed = failed;
    }

    @Override
    public Void call() throws Exception {
      try (Repository repo = repoManager.openRepository(project);
          ReviewDb db = schemaFactory.open()) {
        // Order of scanning changes is undefined. This is ok if we assume that packfile locality is
        // not important for indexing, since sites should have a fully populated DiffSummary cache.
        // It does mean that reindexing after invalidating the DiffSummary cache will be expensive,
        // but the goal is to invalidate that cache as infrequently as we possibly can. And besides,
        // we don't have concrete proof that improving packfile locality would help.
        notesFactory.scan(repo, db, project).forEach(r -> index(db, r));
      } catch (RepositoryNotFoundException rnfe) {
        log.error(rnfe.getMessage());
      }
      return null;
    }

    private void index(ReviewDb db, ChangeNotesResult r) {
      if (r.error().isPresent()) {
        fail("Failed to read change " + r.id() + " for indexing", true, r.error().get());
        return;
      }
      try {
        indexer.index(changeDataFactory.create(db, r.notes()));
        done.update(1);
        verboseWriter.println("Reindexed change " + r.id());
      } catch (RejectedExecutionException e) {
        // Server shutdown, don't spam the logs.
        failSilently();
      } catch (Exception e) {
        fail("Failed to index change " + r.id(), true, e);
      }
    }

    private void fail(String error, boolean failed, Exception e) {
      if (failed) {
        this.failed.update(1);
      }

      if (e != null) {
        log.warn(error, e);
      } else {
        log.warn(error);
      }

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
}
