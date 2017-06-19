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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.FormatUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder.NoPatchSetsException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rebuilder for all changes in a site. */
public class SiteRebuilder implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SiteRebuilder.class);

  public interface Factory {
    SiteRebuilder create(
        int threads,
        @Nullable Collection<Project.NameKey> projects,
        @Nullable Collection<Change.Id> changes);
  }

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeRebuilder rebuilder;
  private final ChangeBundleReader bundleReader;

  private final ListeningExecutorService executor;
  private final ImmutableList<Project.NameKey> projects;
  private final ImmutableList<Change.Id> changes;

  @Inject
  SiteRebuilder(
      SchemaFactory<ReviewDb> schemaFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeRebuilder rebuilder,
      ChangeBundleReader bundleReader,
      WorkQueue workQueue,
      @Assisted int threads,
      @Assisted @Nullable Collection<Project.NameKey> projects,
      @Assisted @Nullable Collection<Change.Id> changes) {
    this.schemaFactory = schemaFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.rebuilder = rebuilder;
    this.bundleReader = bundleReader;
    this.executor =
        threads > 0
            ? MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "RebuildChange"))
            : MoreExecutors.newDirectExecutorService();
    this.projects = projects != null ? ImmutableList.copyOf(projects) : ImmutableList.of();
    this.changes = changes != null ? ImmutableList.copyOf(changes) : ImmutableList.of();
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  public boolean rebuild() throws OrmException {
    boolean ok;
    Stopwatch sw = Stopwatch.createStarted();

    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    ImmutableListMultimap<Project.NameKey, Change.Id> changesByProject = getChangesByProject();
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
    return ok;
  }

  private ImmutableListMultimap<Project.NameKey, Change.Id> getChangesByProject()
      throws OrmException {
    // Memoize all changes so we can close the db connection and allow other threads to use the full
    // connection pool.
    try (ReviewDb db = unwrapDb(schemaFactory.open())) {
      SetMultimap<Project.NameKey, Change.Id> out =
          MultimapBuilder.treeKeys(comparing(Project.NameKey::get))
              .treeSetValues(comparing(Change.Id::get))
              .build();
      if (!projects.isEmpty()) {
        checkState(changes.isEmpty());
        return byProject(db.changes().all(), c -> projects.contains(c.getProject()), out);
      }
      if (!changes.isEmpty()) {
        checkState(projects.isEmpty());
        return byProject(db.changes().get(changes), c -> true, out);
      }
      return byProject(db.changes().all(), c -> true, out);
    }
  }

  private static ImmutableListMultimap<Project.NameKey, Change.Id> byProject(
      Iterable<Change> changes,
      Predicate<Change> pred,
      SetMultimap<Project.NameKey, Change.Id> out) {
    Streams.stream(changes).filter(pred).forEach(c -> out.put(c.getProject(), c.getId()));
    return ImmutableListMultimap.copyOf(out);
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
