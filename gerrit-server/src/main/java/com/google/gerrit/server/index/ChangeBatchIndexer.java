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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.index;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.patch.PatchListLoader;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChangeBatchIndexer {
  private static final Logger log =
      LoggerFactory.getLogger(ChangeBatchIndexer.class);

  public static class Result {
    private final long elapsedNanos;
    private final boolean success;
    private final int done;
    private final int failed;

    private Result(Stopwatch sw, boolean success, int done, int failed) {
      this.elapsedNanos = sw.elapsed(TimeUnit.NANOSECONDS);
      this.success = success;
      this.done = done;
      this.failed = failed;
    }

    public boolean success() {
      return success;
    }

    public int doneCount() {
      return done;
    }

    public int failedCount() {
      return failed;
    }

    public long elapsed(TimeUnit timeUnit) {
      return timeUnit.convert(elapsedNanos, TimeUnit.NANOSECONDS);
    }
  }

  private final Provider<ReviewDb> dbProvider;
  private final GitRepositoryManager repoManager;
  private final ListeningScheduledExecutorService executor;
  private final ChangeIndexer.Factory indexerFactory;

  @Inject
  ChangeBatchIndexer(Provider<ReviewDb> dbProvider,
      GitRepositoryManager repoManager,
      @IndexExecutor ListeningScheduledExecutorService executor,
      ChangeIndexer.Factory indexerFactory) {
    this.dbProvider = dbProvider;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexerFactory = indexerFactory;
  }

  public Result indexAll(ChangeIndex index, Iterable<Project.NameKey> projects,
      int numProjects, int numChanges, OutputStream progressOut) {
    Stopwatch sw = new Stopwatch().start();
    final MultiProgressMonitor mpm =
        new MultiProgressMonitor(System.out, "Reindexing changes");
    final Task projTask = mpm.beginSubTask("projects",
        numProjects >= 0 ? numProjects : MultiProgressMonitor.UNKNOWN);
    final Task doneTask = mpm.beginSubTask(null,
        numChanges >= 0 ? numChanges : MultiProgressMonitor.UNKNOWN);
    final Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    final List<ListenableFuture<?>> futures = Lists.newArrayList();
    final AtomicBoolean ok = new AtomicBoolean(true);

    for (final Project.NameKey project : projects) {
      final ListenableFuture<?> future = executor.submit(new ReindexProject(
          indexerFactory.create(index), project, doneTask, failedTask));
      futures.add(future);
      future.addListener(new Runnable() {
        @Override
        public void run() {
          try {
            future.get();
          } catch (InterruptedException e) {
            fail(project, e);
          } catch (ExecutionException e) {
            ok.set(false); // Logged by indexer.
          } catch (RuntimeException e) {
            failAndThrow(project, e);
          } catch (Error e) {
            failAndThrow(project, e);
          } finally {
            projTask.update(1);
          }
        }

        private void fail(Project.NameKey project, Throwable t) {
          log.error("Failed to index project " + project, t);
          ok.set(false);
        }

        private void failAndThrow(Project.NameKey project, RuntimeException e) {
          fail(project, e);
          throw e;
        }

        private void failAndThrow(Project.NameKey project, Error e) {
          fail(project, e);
          throw e;
        }
      }, MoreExecutors.sameThreadExecutor());
    }

    try {
      mpm.waitFor(Futures.transform(Futures.successfulAsList(futures),
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
    return new Result(sw, ok.get(), doneTask.getCount(), failedTask.getCount());
  }

  private class ReindexProject implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final Project.NameKey project;
    private final ListMultimap<ObjectId, ChangeData> byId;
    private final Task done;
    private final Task failed;
    private Repository repo;
    private RevWalk walk;

    private ReindexProject(ChangeIndexer indexer, Project.NameKey project,
        Task done, Task failed) {
      this.indexer = indexer;
      this.project = project;
      this.byId = ArrayListMultimap.create();
      this.done = done;
      this.failed = failed;
    }

    @Override
    public Void call() throws Exception {
      ReviewDb db = dbProvider.get();
      repo = repoManager.openRepository(project);

      try {
        Map<String, Ref> refs = repo.getAllRefs();
        for (Change c : db.changes().byProject(project)) {
          Ref r = refs.get(c.currentPatchSetId().toRefName());
          if (r != null) {
            byId.put(r.getObjectId(), new ChangeData(c));
          }
        }
        walk();
      } finally {
        repo.close();
        // TODO(dborowitz): Opening all repositories in a live server may be
        // wasteful; see if we can determine which ones it is safe to cose with
        // RepositoryCache.close(repo).
      }
      return null;
    }

    private void walk() throws Exception {
      walk = new RevWalk(repo);
      try {
        // Walk only refs first to cover as many changes as we can without having
        // to mark every single change.
        for (Ref ref : repo.getRefDatabase().getRefs(Constants.R_HEADS).values()) {
          RevObject o = walk.parseAny(ref.getObjectId());
          if (o instanceof RevCommit) {
            walk.markStart((RevCommit) o);
          }
        }

        RevCommit bCommit;
        while ((bCommit = walk.next()) != null && !byId.isEmpty()) {
          if (byId.containsKey(bCommit)) {
            getPathsAndIndex(bCommit);
            byId.removeAll(bCommit);
          }
        }

        for (ObjectId id : byId.keySet()) {
          getPathsAndIndex(walk.parseCommit(id));
        }
      } finally {
        walk.release();
      }
    }

    private void getPathsAndIndex(RevCommit bCommit) throws Exception {
      RevTree bTree = bCommit.getTree();
      try {
        RevTree aTree = aFor(bCommit, walk);
        if (aTree == null) {
          return;
        }
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        try {
          df.setRepository(repo);
          List<ChangeData> cds = byId.get(bCommit);
          if (!cds.isEmpty()) {
            List<String> paths = getPaths(df.scan(aTree, bTree));
            for (ChangeData cd : cds) {
              cd.setCurrentFilePaths(paths);
              indexer.indexTask(cd).call();
              done.update(1);
            }
          }
        } finally {
          df.release();
        }
      } catch (Exception e) {
        log.warn("Failed to index changes for commit " + bCommit.name(), e);
        failed.update(1);
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

    private RevTree aFor(RevCommit b, RevWalk walk) throws IOException {
      switch (b.getParentCount()) {
        case 0:
          return walk.parseTree(emptyTree());
        case 1:
          RevCommit a = b.getParent(0);
          walk.parseBody(a);
          return walk.parseTree(a.getTree());
        case 2:
          return PatchListLoader.automerge(repo, walk, b);
        default:
          return null;
      }
    }

    private ObjectId emptyTree() throws IOException {
      ObjectInserter oi = repo.newObjectInserter();
      try {
        ObjectId id = oi.insert(Constants.OBJ_TREE, new byte[] {});
        oi.flush();
        return id;
      } finally {
        oi.release();
      }
    }
  }
}
