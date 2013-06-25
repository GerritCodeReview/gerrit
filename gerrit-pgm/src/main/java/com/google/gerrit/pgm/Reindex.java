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

package com.google.gerrit.pgm;

import static com.google.gerrit.lucene.IndexVersionCheck.SCHEMA_VERSIONS;
import static com.google.gerrit.lucene.IndexVersionCheck.gerritIndexConfig;
import static com.google.gerrit.lucene.LuceneChangeIndex.LUCENE_VERSION;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.patch.PatchListLoader;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Reindex extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(Reindex.class);

  @Option(name = "--threads", usage = "Number of threads to use for indexing")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--dry-run", usage = "Dry run: don't write anything to index")
  private boolean dryRun;

  private Injector dbInjector;
  private Injector sysInjector;
  private SitePaths sitePaths;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(SINGLE_USER);
    if (!IndexModule.isEnabled(dbInjector)) {
      throw die("Secondary index not enabled");
    }
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();
    sitePaths = dbInjector.getInstance(SitePaths.class);

    // Delete before any index may be created depending on this data.
    deleteAll();

    sysInjector = createSysInjector();
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    int result = indexAll();
    writeVersion();

    sysManager.stop();
    dbManager.stop();
    return result;
  }

  private Injector createSysInjector() {
    List<Module> modules = Lists.newArrayList();
    modules.add(PatchListCacheImpl.module());
    modules.add(new LuceneIndexModule(false, threads, dryRun));
    modules.add(new ReviewDbModule());
    modules.add(new AbstractModule() {
      @SuppressWarnings("rawtypes")
      @Override
      protected void configure() {
        // Plugins are not loaded and we're just running through each change
        // once, so don't worry about cache removal.
        bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
            .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
        install(new DefaultCacheFactory.Module());
      }
    });
    return dbInjector.createChildInjector(modules);
  }

  private class ReviewDbModule extends LifecycleModule {
    @Override
    protected void configure() {
      final SchemaFactory<ReviewDb> schema = dbInjector.getInstance(
          Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
      final List<ReviewDb> dbs = Collections.synchronizedList(
          Lists.<ReviewDb> newArrayListWithCapacity(threads + 1));
      final ThreadLocal<ReviewDb> localDb = new ThreadLocal<ReviewDb>();

      bind(ReviewDb.class).toProvider(new Provider<ReviewDb>() {
        @Override
        public ReviewDb get() {
          ReviewDb db = localDb.get();
          if (db == null) {
            try {
              db = schema.open();
              dbs.add(db);
              localDb.set(db);
            } catch (OrmException e) {
              throw new ProvisionException("unable to open ReviewDb", e);
            }
          }
          return db;
        }
      });
      listener().toInstance(new LifecycleListener() {
        @Override
        public void start() {
          // Do nothing.
        }

        @Override
        public void stop() {
          for (ReviewDb db : dbs) {
            db.close();
          }
        }
      });
    }
  }

  private void deleteAll() throws IOException {
    if (dryRun) {
      return;
    }
    for (String index : SCHEMA_VERSIONS.keySet()) {
      File file = new File(sitePaths.index_dir, index);
      if (file.exists()) {
        Directory dir = FSDirectory.open(file);
        try {
          for (String name : dir.listAll()) {
            dir.deleteFile(name);
          }
        } finally {
          dir.close();
        }
      }
    }
  }

  private int indexAll() throws Exception {
    ReviewDb db = sysInjector.getInstance(ReviewDb.class);
    ListeningScheduledExecutorService executor = sysInjector.getInstance(
        Key.get(ListeningScheduledExecutorService.class, IndexExecutor.class));

    ProgressMonitor pm = new TextProgressMonitor(new PrintWriter(System.out));
    pm.start(1);
    pm.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
    Set<Project.NameKey> projects;
    try {
      projects = Sets.newTreeSet();
      for (Change change : db.changes().all()) {
        int n = projects.size();
        projects.add(change.getProject());
        int d = projects.size() - n;
        if (d > 0) {
          pm.update(d);
        }
      }
    } finally {
      db.close();
    }
    pm.endTask();

    final MultiProgressMonitor mpm =
        new MultiProgressMonitor(System.out, "Reindexing changes");
    final Task projTask = mpm.beginSubTask("projects", projects.size());
    final Task doneTask = mpm.beginSubTask(null, MultiProgressMonitor.UNKNOWN);
    final Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    Stopwatch sw = new Stopwatch().start();
    final CountDownLatch latch = new CountDownLatch(projects.size());
    final AtomicBoolean ok = new AtomicBoolean(true);

    for (final Project.NameKey project : projects) {
      final ListenableFuture<?> future = executor.submit(
          new ReindexProject(project, doneTask, failedTask));
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
            latch.countDown();
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

    mpm.waitFor(sysInjector.getInstance(WorkQueue.class).getDefaultQueue()
        .submit(new Runnable() {
          @Override
          public void run() {
            try {
              latch.await();
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            mpm.end();
          }
        }));
    double elapsed = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    int n = doneTask.getCount() + failedTask.getCount();
    System.out.format("Reindexed %d changes in %.01fs (%.01f/s)\n",
        n, elapsed, n/elapsed);

    return ok.get() ? 0 : 1;
  }

  private class ReindexProject implements Callable<Void> {
    private final ChangeIndexer indexer;
    private final Project.NameKey project;
    private final ListMultimap<ObjectId, ChangeData> byId;
    private final Task done;
    private final Task failed;
    private Repository repo;
    private RevWalk walk;

    private ReindexProject(Project.NameKey project, Task done, Task failed) {
      this.indexer = sysInjector.getInstance(ChangeIndexer.class);
      this.project = project;
      this.byId = ArrayListMultimap.create();
      this.done = done;
      this.failed = failed;
    }

    @Override
    public Void call() throws Exception {
      ReviewDb db = sysInjector.getInstance(ReviewDb.class);
      GitRepositoryManager mgr = sysInjector.getInstance(GitRepositoryManager.class);
      repo = mgr.openRepository(project);

      try {
        Map<String, Ref> refs = repo.getAllRefs();
        for (Change c : db.changes().all()) {
          Ref r = refs.get(c.currentPatchSetId().toRefName());
          if (r != null) {
            byId.put(r.getObjectId(), new ChangeData(c));
          }
        }
        walk();
      } finally {
        repo.close();
        RepositoryCache.close(repo); // Only used once per Reindex call.
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
          if (!(o instanceof RevCommit)) {
            continue;
          }
          walk.markStart((RevCommit) o);
        }

        RevCommit bCommit;
        while ((bCommit = walk.next()) != null) {
          if (!byId.containsKey(bCommit)) {
            continue; // Not a change, skip diff.
          }
          getPathsAndIndex(bCommit, true);
          if (byId.isEmpty()) {
            break;
          }
        }

        for (ObjectId id : byId.keySet()) {
          getPathsAndIndex(walk.parseCommit(id), false);
        }
      } finally {
        walk.release();
      }
    }

    private void getPathsAndIndex(RevCommit bCommit, boolean remove)
        throws Exception {
      RevTree bTree = bCommit.getTree();
      try {
        RevTree aTree = aFor(bCommit, walk);
        if (aTree == null) {
          return;
        }
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        try {
          df.setRepository(repo);
          List<ChangeData> cds = remove ? byId.removeAll(bCommit)
              : byId.get(bCommit);
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

  private void writeVersion() throws IOException,
      ConfigInvalidException {
    if (dryRun) {
      return;
    }
    FileBasedConfig cfg =
        new FileBasedConfig(gerritIndexConfig(sitePaths), FS.detect());
    cfg.load();

    for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
      cfg.setInt("index", e.getKey(), "schemaVersion", e.getValue());
    }
    cfg.setEnum("lucene", null, "version", LUCENE_VERSION);
    cfg.save();
  }
}
