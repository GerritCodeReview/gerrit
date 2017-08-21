// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.PrimaryStorageMigrator;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

public class MigrateToReviewDb extends SiteProgram {
  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  private Injector dbInjector;
  private Injector sysInjector;
  private LifecycleManager dbManager;
  private LifecycleManager sysManager;

  @Inject @GerritServerConfig private Config cfg;
  @Inject private OneOffRequestContext requestContext;
  @Inject private ProjectCache projectCache;
  @Inject private GitRepositoryManager repoManager;
  @Inject private PrimaryStorageMigrator migrator;
  @Inject private SitePaths sitePaths;

  @AutoValue
  abstract static class TaskId {
    static TaskId create(Project.NameKey project, Change.Id changeId) {
      return new AutoValue_MigrateToReviewDb_TaskId(project, changeId);
    }

    abstract Project.NameKey project();

    abstract Change.Id changeId();
  }

  @Override
  public int run() throws Exception {
    RuntimeShutdown.add(this::stop);

    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
    sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    ListeningExecutorService executor =
        MoreExecutors.listeningDecorator(Executors.newWorkStealingPool(threads));

    try {
      NotesMigrationState s =
          NotesMigrationState.forConfig(cfg)
              .orElseThrow(() -> new IllegalStateException("Couldn't parse existing NoteDb state"));
      if (s.compareTo(READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY) < 0) {
        throw die("Must be in NoteDb with sequences enabled, found " + s);
      }

      List<TaskId> ids = new ArrayList<>();
      ProgressMonitor pm = new TextProgressMonitor(new OutputStreamWriter(System.err));
      pm.start(1);
      pm.beginTask("Collecting changes", ProgressMonitor.UNKNOWN);
      for (Project.NameKey p : projectCache.all()) {
        try (Repository repo = repoManager.openRepository(p)) {
          for (String ref : repo.getRefDatabase().getRefs(RefDatabase.ALL).keySet()) {
            Change.Id id = Change.Id.fromRef(ref);
            if (id == null || !RefNames.changeMetaRef(id).equals(ref)) {
              continue;
            }
            ids.add(TaskId.create(p, id));
            pm.update(1);
          }
        }
      }
      pm.endTask();

      MultiProgressMonitor mpm = new MultiProgressMonitor(System.err, "Migrating");
      Task doneTask = mpm.beginSubTask(null, ids.size());
      Task failedTask = mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);
      List<ListenableFuture<?>> futures =
          ids.stream()
              .map(id -> new MigrateTask(id, doneTask, failedTask))
              .map(executor::submit)
              .collect(toList());
      mpm.waitFor(Futures.allAsList(futures));

      // TODO: doesn't fix counter state; need to manually advance
      StoredConfig storedConfig =
          new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
      storedConfig.load();
      NotesMigrationState.REVIEW_DB.setConfigValues(storedConfig);
      storedConfig.save();

      return 0;
    } finally {
      stop();
    }
  }

  private class MigrateTask implements Runnable {
    private final TaskId id;
    private final Task doneTask;
    private final Task failedTask;

    MigrateTask(TaskId id, Task doneTask, Task failedTask) {
      this.id = id;
      this.doneTask = doneTask;
      this.failedTask = failedTask;
    }

    @Override
    public void run() {
      try (ManualRequestContext ctx = requestContext.open()) {
        try {
          migrator.migrateToReviewDbPrimary(id.changeId(), id.project());
          doneTask.update(1);
        } catch (OrmException | IOException e) {
          System.out.println("Failed to migrate change " + id);
          e.printStackTrace();
          failedTask.update(1);
        }
      } catch (OrmException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
            install(dbInjector.getInstance(BatchProgramModule.class));
            install(new DummyIndexModule());
            factory(ChangeResource.Factory.class);
          }
        });
  }

  private void stop() {
    try {
      LifecycleManager m = sysManager;
      sysManager = null;
      if (m != null) {
        m.stop();
      }
    } finally {
      LifecycleManager m = dbManager;
      dbManager = null;
      if (m != null) {
        m.stop();
      }
    }
  }
}
