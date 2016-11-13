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

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;
import static org.eclipse.jgit.lib.RefDatabase.ALL;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.SiteIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
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
  private final ThreeWayMergeStrategy mergeStrategy;
  private final AutoMerger autoMerger;

  @Inject
  AllChangesIndexer(
      SchemaFactory<ReviewDb> schemaFactory,
      ChangeData.Factory changeDataFactory,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ChangeIndexer.Factory indexerFactory,
      ChangeNotes.Factory notesFactory,
      @GerritServerConfig Config config,
      ProjectCache projectCache,
      AutoMerger autoMerger) {
    this.schemaFactory = schemaFactory;
    this.changeDataFactory = changeDataFactory;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexerFactory = indexerFactory;
    this.notesFactory = notesFactory;
    this.projectCache = projectCache;
    this.mergeStrategy = MergeUtil.getMergeStrategy(config);
    this.autoMerger = autoMerger;
  }

  private static class ProjectHolder implements Comparable<ProjectHolder> {
    private Project.NameKey name;
    private int size;

    ProjectHolder(Project.NameKey name, int size) {
      this.name = name;
      this.size = size;
    }

    @Override
    public int compareTo(ProjectHolder other) {
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
    for (Project.NameKey name : projectCache.all()) {
      try (Repository repo = repoManager.openRepository(name)) {
        int size = ChangeNotes.Factory.scan(repo).size();
        changeCount += size;
        projects.add(new ProjectHolder(name, size));
      } catch (IOException e) {
        log.error("Error collecting projects", e);
        return new Result(sw, false, 0, 0);
      }
      pm.update(1);
    }
    pm.endTask();
    setTotalWork(changeCount);

    return indexAll(index, projects);
  }

  public SiteIndexer.Result indexAll(ChangeIndex index, Iterable<ProjectHolder> projects) {
    Stopwatch sw = Stopwatch.createStarted();
    final MultiProgressMonitor mpm = new MultiProgressMonitor(progressOut, "Reindexing changes");
    final Task projTask =
        mpm.beginSubTask(
            "projects",
            (projects instanceof Collection)
                ? ((Collection<?>) projects).size()
                : MultiProgressMonitor.UNKNOWN);
    final Task doneTask =
        mpm.beginSubTask(null, totalWork >= 0 ? totalWork : MultiProgressMonitor.UNKNOWN);
    final Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    final List<ListenableFuture<?>> futures = new ArrayList<>();
    final AtomicBoolean ok = new AtomicBoolean(true);

    for (final ProjectHolder project : projects) {
      ListenableFuture<?> future =
          executor.submit(
              reindexProject(
                  indexerFactory.create(executor, index),
                  project.name,
                  doneTask,
                  failedTask,
                  verboseWriter));
      addErrorListener(future, "project " + project.name, projTask, ok);
      futures.add(future);
    }

    try {
      mpm.waitFor(
          Futures.transformAsync(
              Futures.successfulAsList(futures),
              new AsyncFunction<List<?>, Void>() {
                @Override
                public ListenableFuture<Void> apply(List<?> input) {
                  mpm.end();
                  return Futures.immediateFuture(null);
                }
              }));
    } catch (ExecutionException e) {
      log.error("Error in batch indexer", e);
      ok.set(false);
    }
    // If too many changes failed, maybe there was a bug in the indexer. Don't
    // trust the results. This is not an exact percentage since we bump the same
    // failure counter if a project can't be read, but close enough.
    int nFailed = failedTask.getCount();
    int nTotal = nFailed + doneTask.getCount();
    double pctFailed = ((double) nFailed) / nTotal * 100;
    if (pctFailed > 10) {
      log.error(
          "Failed {}/{} changes ({}%); not marking new index as ready",
          nFailed, nTotal, Math.round(pctFailed));
      ok.set(false);
    }
    return new Result(sw, ok.get(), doneTask.getCount(), failedTask.getCount());
  }

  private Callable<Void> reindexProject(
      final ChangeIndexer indexer,
      final Project.NameKey project,
      final Task done,
      final Task failed,
      final PrintWriter verboseWriter) {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        Multimap<ObjectId, ChangeData> byId = ArrayListMultimap.create();
        // TODO(dborowitz): Opening all repositories in a live server may be
        // wasteful; see if we can determine which ones it is safe to close
        // with RepositoryCache.close(repo).
        try (Repository repo = repoManager.openRepository(project);
            ReviewDb db = schemaFactory.open()) {
          Map<String, Ref> refs = repo.getRefDatabase().getRefs(ALL);
          // TODO(dborowitz): Pre-loading all notes is almost certainly a
          // terrible idea for performance. If we can get rid of walking by
          // commit (see note below), then all we need to discover here is the
          // change IDs.
          for (ChangeNotes cn : notesFactory.scan(repo, db, project)) {
            Ref r = refs.get(cn.getChange().currentPatchSetId().toRefName());
            if (r != null) {
              byId.put(r.getObjectId(), changeDataFactory.create(db, cn));
            }
          }
          new ProjectIndexer(
                  indexer, mergeStrategy, autoMerger, byId, repo, done, failed, verboseWriter)
              .call();
        } catch (RepositoryNotFoundException rnfe) {
          log.error(rnfe.getMessage());
        }
        return null;
      }

      @Override
      public String toString() {
        return "Index all changes of project " + project.get();
      }
    };
  }

  private static class ProjectIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final ThreeWayMergeStrategy mergeStrategy;
    private final AutoMerger autoMerger;
    private final Multimap<ObjectId, ChangeData> byId;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;
    private final PrintWriter verboseWriter;
    private final Repository repo;

    private ProjectIndexer(
        ChangeIndexer indexer,
        ThreeWayMergeStrategy mergeStrategy,
        AutoMerger autoMerger,
        Multimap<ObjectId, ChangeData> changesByCommitId,
        Repository repo,
        ProgressMonitor done,
        ProgressMonitor failed,
        PrintWriter verboseWriter) {
      this.indexer = indexer;
      this.mergeStrategy = mergeStrategy;
      this.autoMerger = autoMerger;
      this.byId = changesByCommitId;
      this.repo = repo;
      this.done = done;
      this.failed = failed;
      this.verboseWriter = verboseWriter;
    }

    @Override
    public Void call() throws Exception {
      try (ObjectInserter ins = repo.newObjectInserter();
          RevWalk walk = new RevWalk(ins.newReader())) {
        // Walk only refs first to cover as many changes as we can without having
        // to mark every single change.
        for (Ref ref : repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
          RevObject o = walk.parseAny(ref.getObjectId());
          if (o instanceof RevCommit) {
            walk.markStart((RevCommit) o);
          }
        }

        // TODO(dborowitz): This is basically pointless; it computes
        // currentFilePaths faster than going through PatchListCache, but we
        // still need to go through PatchListCache for changedLines.
        RevCommit bCommit;
        while ((bCommit = walk.next()) != null && !byId.isEmpty()) {
          if (byId.containsKey(bCommit)) {
            getPathsAndIndex(walk, ins, bCommit);
            byId.removeAll(bCommit);
          }
        }

        for (ObjectId id : byId.keySet()) {
          getPathsAndIndex(walk, ins, id);
        }
      }
      return null;
    }

    private void getPathsAndIndex(RevWalk walk, ObjectInserter ins, ObjectId b) throws Exception {
      List<ChangeData> cds = Lists.newArrayList(byId.get(b));
      try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        RevCommit bCommit = walk.parseCommit(b);
        RevTree bTree = bCommit.getTree();
        RevTree aTree = aFor(bCommit, walk, ins);
        df.setRepository(repo);
        if (!cds.isEmpty()) {
          List<String> paths =
              (aTree != null) ? getPaths(df.scan(aTree, bTree)) : Collections.<String>emptyList();
          Iterator<ChangeData> cdit = cds.iterator();
          for (ChangeData cd; cdit.hasNext(); cdit.remove()) {
            cd = cdit.next();
            try {
              cd.setCurrentFilePaths(paths);
              indexer.index(cd);
              done.update(1);
              if (verboseWriter != null) {
                verboseWriter.println("Reindexed change " + cd.getId());
              }
            } catch (Exception e) {
              fail("Failed to index change " + cd.getId(), true, e);
            }
          }
        }
      } catch (Exception e) {
        fail("Failed to index commit " + b.name(), false, e);
        for (ChangeData cd : cds) {
          fail("Failed to index change " + cd.getId(), true, null);
        }
      }
    }

    private List<String> getPaths(List<DiffEntry> filenames) {
      Set<String> paths = Sets.newTreeSet();
      for (DiffEntry e : filenames) {
        if (e.getOldPath() != null) {
          paths.add(e.getOldPath());
        }
        if (e.getNewPath() != null) {
          paths.add(e.getNewPath());
        }
      }
      return ImmutableList.copyOf(paths);
    }

    private RevTree aFor(RevCommit b, RevWalk walk, ObjectInserter ins) throws IOException {
      switch (b.getParentCount()) {
        case 0:
          return walk.parseTree(emptyTree());
        case 1:
          RevCommit a = b.getParent(0);
          walk.parseBody(a);
          return walk.parseTree(a.getTree());
        case 2:
          RevCommit am = autoMerger.merge(repo, walk, ins, b, mergeStrategy);
          return am == null ? null : am.getTree();
        default:
          return null;
      }
    }

    private ObjectId emptyTree() throws IOException {
      try (ObjectInserter oi = repo.newObjectInserter()) {
        ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
        oi.flush();
        return id;
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

      if (verboseWriter != null) {
        verboseWriter.println(error);
      }
    }
  }
}
