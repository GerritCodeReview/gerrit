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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.FormatUtil;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder.NoPatchSetsException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RebuildNoteDb extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(RebuildNoteDb.class);

  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--project", usage = "Projects to rebuild; recommended for debugging only")
  private List<String> projects = new ArrayList<>();

  @Option(
    name = "--change",
    usage = "Individual change numbers to rebuild; recommended for debugging only"
  )
  private List<Integer> changes = new ArrayList<>();

  private Injector dbInjector;
  private Injector sysInjector;

  @Inject private ChangeRebuilder rebuilder;

  @Inject private NoteDbUpdateManager.Factory updateManagerFactory;

  @Inject private NotesMigration notesMigration;

  @Inject private SchemaFactory<ReviewDb> schemaFactory;

  @Inject private WorkQueue workQueue;

  @Inject private ChangeBundleReader bundleReader;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
    if (!notesMigration.enabled()) {
      throw die("NoteDb is not enabled.");
    }
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    ListeningExecutorService executor = newExecutor();
    System.out.println("Rebuilding the NoteDb");

    ImmutableListMultimap<Project.NameKey, Change.Id> changesByProject = getChangesByProject();
    boolean ok;
    Stopwatch sw = Stopwatch.createStarted();

    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    List<Project.NameKey> projectNames =
        Ordering.usingToString().sortedCopy(changesByProject.keySet());
    for (Project.NameKey project : projectNames) {
      ListenableFuture<Boolean> future =
          executor.submit(
              () -> {
                try (ReviewDb db = unwrapDb(schemaFactory.open())) {
                  return rebuildProject(db, changesByProject, project);
                } catch (Exception e) {
                  log.error("Error rebuilding project " + project, e);
                  return false;
                }
              });
      futures.add(future);
    }

    try {
      ok = Iterables.all(Futures.allAsList(futures).get(), Predicates.equalTo(true));
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error rebuilding projects", e);
      ok = false;
    }

    double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format(
        "Rebuild %d changes in %.01fs (%.01f/s)\n",
        changesByProject.size(), t, changesByProject.size() / t);
    return ok ? 0 : 1;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            install(dbInjector.getInstance(BatchProgramModule.class));
            DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
                .to(ReindexAfterRefUpdate.class);
            install(new DummyIndexModule());
            factory(ChangeResource.Factory.class);
          }
        });
  }

  private ListeningExecutorService newExecutor() {
    if (threads > 0) {
      return MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "RebuildChange"));
    }
    return MoreExecutors.newDirectExecutorService();
  }

  private ImmutableListMultimap<Project.NameKey, Change.Id> getChangesByProject()
      throws OrmException {
    // Memorize all changes so we can close the db connection and allow
    // rebuilder threads to use the full connection pool.
    ListMultimap<Project.NameKey, Change.Id> changesByProject =
        MultimapBuilder.hashKeys().arrayListValues().build();
    try (ReviewDb db = schemaFactory.open()) {
      if (projects.isEmpty() && !changes.isEmpty()) {
        Iterable<Change> todo =
            unwrapDb(db).changes().get(Iterables.transform(changes, Change.Id::new));
        for (Change c : todo) {
          changesByProject.put(c.getProject(), c.getId());
        }
      } else {
        for (Change c : unwrapDb(db).changes().all()) {
          boolean include = false;
          if (projects.isEmpty() && changes.isEmpty()) {
            include = true;
          } else if (!projects.isEmpty() && projects.contains(c.getProject().get())) {
            include = true;
          } else if (!changes.isEmpty() && changes.contains(c.getId().get())) {
            include = true;
          }
          if (include) {
            changesByProject.put(c.getProject(), c.getId());
          }
        }
      }
      return ImmutableListMultimap.copyOf(changesByProject);
    }
  }

  private boolean rebuildProject(
      ReviewDb db,
      ImmutableListMultimap<Project.NameKey, Change.Id> allChanges,
      Project.NameKey project)
      throws IOException, OrmException {
    checkArgument(allChanges.containsKey(project));
    boolean ok = true;
    ProgressMonitor pm =
        new TextProgressMonitor(
            new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8))));
    pm.beginTask(FormatUtil.elide(project.get(), 50), allChanges.get(project).size());
    try (NoteDbUpdateManager manager = updateManagerFactory.create(project)) {
      for (Change.Id changeId : allChanges.get(project)) {
        try {
          rebuilder.buildUpdates(manager, bundleReader.fromReviewDb(db, changeId));
        } catch (NoPatchSetsException e) {
          log.warn(e.getMessage());
        } catch (Throwable t) {
          log.error("Failed to rebuild change " + changeId, t);
          ok = false;
        }
        pm.update(1);
      }
      manager.execute();
    } finally {
      pm.endTask();
    }
    return ok;
  }
}
