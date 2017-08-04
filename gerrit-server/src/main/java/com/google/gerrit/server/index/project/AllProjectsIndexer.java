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

package com.google.gerrit.server.index.project;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.SiteIndexer;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AllProjectsIndexer extends SiteIndexer<Project.NameKey, ProjectData, ProjectIndex> {

  private static final Logger log = LoggerFactory.getLogger(AllProjectsIndexer.class);

  private final ListeningExecutorService executor;
  private final ProjectCache projectCache;

  @Inject
  AllProjectsIndexer(
      @IndexExecutor(BATCH) ListeningExecutorService executor, ProjectCache projectCache) {
    this.executor = executor;
    this.projectCache = projectCache;
  }

  @Override
  public SiteIndexer.Result indexAll(final ProjectIndex index) {
    ProgressMonitor progress = new TextProgressMonitor(new PrintWriter(progressOut));
    progress.start(2);
    Stopwatch sw = Stopwatch.createStarted();
    List<Project.NameKey> names;
    try {
      names = collectProjects(progress);
    } catch (OrmException e) {
      log.error("Error collecting projects", e);
      return new SiteIndexer.Result(sw, false, 0, 0);
    }
    return reindexProjects(index, names, progress);
  }

  private SiteIndexer.Result reindexProjects(
      ProjectIndex index, List<Project.NameKey> names, ProgressMonitor progress) {
    progress.beginTask("Reindexing projects", names.size());
    List<ListenableFuture<?>> futures = new ArrayList<>(names.size());
    AtomicBoolean ok = new AtomicBoolean(true);
    AtomicInteger done = new AtomicInteger();
    AtomicInteger failed = new AtomicInteger();
    Stopwatch sw = Stopwatch.createStarted();
    for (Project.NameKey name : names) {
      String desc = "project " + name;
      ListenableFuture<?> future =
          executor.submit(
              () -> {
                try {
                  projectCache.evict(name);
                  index.replace(projectCache.get(name).toProjectData());
                  verboseWriter.println("Reindexed " + desc);
                  done.incrementAndGet();
                } catch (Exception e) {
                  failed.incrementAndGet();
                  throw e;
                }
                return null;
              });
      addErrorListener(future, desc, progress, ok);
      futures.add(future);
    }

    try {
      Futures.successfulAsList(futures).get();
    } catch (ExecutionException | InterruptedException e) {
      log.error("Error waiting on project futures", e);
      return new SiteIndexer.Result(sw, false, 0, 0);
    }

    progress.endTask();
    return new SiteIndexer.Result(sw, ok.get(), done.get(), failed.get());
  }

  private List<Project.NameKey> collectProjects(ProgressMonitor progress) throws OrmException {
    progress.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
    List<Project.NameKey> names = new ArrayList<>();
    for (Project.NameKey nameKey : projectCache.all()) {
      names.add(nameKey);
    }
    progress.endTask();
    return names;
  }
}
