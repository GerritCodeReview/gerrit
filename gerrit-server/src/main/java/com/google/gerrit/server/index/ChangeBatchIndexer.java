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
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gerrit.server.patch.PatchScriptBuilder;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager repoManager;
  private final ListeningScheduledExecutorService executor;
  private final ChangeIndexer.Factory indexerFactory;
  private final PatchListCache patchListCache;
  private final PatchScriptBuilder patchScriptBuilder;

  @Inject
  ChangeBatchIndexer(Provider<ReviewDb> db,
      GitRepositoryManager repoManager,
      @IndexExecutor ListeningScheduledExecutorService executor,
      ChangeIndexer.Factory indexerFactory,
      PatchListCache patchListCache,
      PatchScriptBuilder patchScriptBuilder) {
    this.db = db;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexerFactory = indexerFactory;
    this.patchListCache = patchListCache;
    this.patchScriptBuilder = patchScriptBuilder;
  }

  public Result indexAll(ChangeIndex index, Iterable<Project.NameKey> projects,
      int numProjects, int numChanges, OutputStream progressOut,
      OutputStream verboseOut) {
    if (progressOut == null) {
      progressOut = NullOutputStream.INSTANCE;
    }
    PrintWriter verboseWriter = verboseOut != null ? new PrintWriter(verboseOut)
        : null;

    Stopwatch sw = Stopwatch.createStarted();
    final MultiProgressMonitor mpm =
        new MultiProgressMonitor(progressOut, "Reindexing changes");
    final Task projTask = mpm.beginSubTask("projects",
        numProjects >= 0 ? numProjects : MultiProgressMonitor.UNKNOWN);
    final Task doneTask = mpm.beginSubTask(null,
        numChanges >= 0 ? numChanges : MultiProgressMonitor.UNKNOWN);
    final Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    final List<ListenableFuture<?>> futures = Lists.newArrayList();
    final AtomicBoolean ok = new AtomicBoolean(true);

    for (final Project.NameKey project : projects) {
      final ListenableFuture<?> future = executor.submit(reindexProject(
          indexerFactory.create(index), project, doneTask, failedTask,
          verboseWriter));
      futures.add(future);
      future.addListener(new Runnable() {
        @Override
        public void run() {
          try {
            future.get();
          } catch (InterruptedException e) {
            fail(project, e);
          } catch (ExecutionException e) {
            fail(project, e);
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

  private Callable<Void> reindexProject(final ChangeIndexer indexer,
      final Project.NameKey project, final Task done, final Task failed,
      final PrintWriter verboseWriter) {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        Multimap<ObjectId, ChangeData> byId = ArrayListMultimap.create();
        Repository repo = repoManager.openRepository(project);
        try {
          Map<String, Ref> refs = repo.getAllRefs();
          for (Change c : db.get().changes().byProject(project)) {
            Ref r = refs.get(c.currentPatchSetId().toRefName());
            if (r != null) {
              byId.put(r.getObjectId(), new ChangeData(c));
            }
          }
          new ProjectIndexer(indexer, patchListCache, patchScriptBuilder,
              project, byId, repo, done, failed, verboseWriter).call();
        } finally {
          repo.close();
          // TODO(dborowitz): Opening all repositories in a live server may be
          // wasteful; see if we can determine which ones it is safe to close
          // with RepositoryCache.close(repo).
        }
        return null;
      }
    };
  }

  public static class ProjectIndexer implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final PatchListCache patchListCache;
    private final PatchScriptBuilder patchScriptBuilder;
    private final Project.NameKey project;
    private final Multimap<ObjectId, ChangeData> byId;
    private final ProgressMonitor done;
    private final ProgressMonitor failed;
    private final PrintWriter verboseWriter;
    private final Repository repo;
    private RevWalk walk;

    public ProjectIndexer(ChangeIndexer indexer, PatchListCache patchListCache,
        PatchScriptBuilder patchScriptBuilder, Project.NameKey project,
        Multimap<ObjectId, ChangeData> changesByCommitId, Repository repo) {
      this(indexer, patchListCache, patchScriptBuilder, project,
          changesByCommitId, repo, NullProgressMonitor.INSTANCE,
          NullProgressMonitor.INSTANCE, null);
    }

    ProjectIndexer(ChangeIndexer indexer, PatchListCache patchListCache,
        PatchScriptBuilder patchScriptBuilder, Project.NameKey project,
        Multimap<ObjectId, ChangeData> changesByCommitId, Repository repo,
        ProgressMonitor done, ProgressMonitor failed, PrintWriter verboseWriter) {
      this.indexer = indexer;
      this.patchListCache = patchListCache;
      this.patchScriptBuilder = patchScriptBuilder;
      this.project = project;
      this.byId = changesByCommitId;
      this.repo = repo;
      this.done = done;
      this.failed = failed;
      this.verboseWriter = verboseWriter;
    }

    @Override
    public Void call() throws Exception {
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
          getPathsAndIndex(id);
        }
      } finally {
        walk.release();
      }
      return null;
    }

    private void getPathsAndIndex(ObjectId b) throws Exception {
      List<ChangeData> cds = Lists.newArrayList(byId.get(b));
      try {
        RevCommit bCommit = walk.parseCommit(b);
        if (!cds.isEmpty()) {
          Iterator<ChangeData> cdit = cds.iterator();
          for (ChangeData cd; cdit.hasNext(); cdit.remove()) {
            cd = cdit.next();
            try {
              PatchList list = patchListCache.get(
                  new PatchListKey(project, null, bCommit, Whitespace.IGNORE_NONE));
              cd.setCurrentFilePaths(list);
              cd.currentDiffContent(repo, project, list, patchListCache,
                  patchScriptBuilder);
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
