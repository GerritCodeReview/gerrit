// Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public class CleanupChangesIndex {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static record Result(int total, int done, int deleted) {}

  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeIndexer.Factory indexerFactory;
  private final ListeningExecutorService executor;
  private final ProjectCache projectCache;
  private final GitRepositoryManager repoManager;

  private volatile int totalProjects;
  private AtomicInteger failedProjects;
  private AtomicInteger processedProjects;
  private volatile int totalIndexDocuments;
  private AtomicInteger processedIndexDocuments;
  private AtomicInteger deletedIndexDocuments;

  @Inject
  CleanupChangesIndex(
      Provider<InternalChangeQuery> queryProvider,
      ChangeIndexer.Factory indexerFactory,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ProjectCache projectCache,
      GitRepositoryManager repoManager) {
    this.queryProvider = queryProvider;
    this.indexerFactory = indexerFactory;
    this.executor = executor;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
  }

  public ListenableFuture<Result> cleanupAsync(ChangeIndex index) {
    return executor.submit(() -> cleanup(index));
  }

  public Result cleanup(ChangeIndex index) {
    logger.atInfo().log("Start cleaning up changes index");
    ChangeIndexer indexer = indexerFactory.create(executor, index, false);
    ImmutableSortedSet<NameKey> all = projectCache.all();
    totalProjects = all.size();
    failedProjects = new AtomicInteger();
    processedProjects = new AtomicInteger();
    totalIndexDocuments = index.numDocs();
    processedIndexDocuments = new AtomicInteger();
    deletedIndexDocuments = new AtomicInteger();

    ArrayList<ListenableFuture<?>> cleanupTasks = new ArrayList<>(all.size());
    for (Project.NameKey name : all) {
      cleanupTasks.add(executor.submit(() -> cleanupForProject(name, indexer)));
    }
    try {
      Futures.successfulAsList(cleanupTasks).get();
    } catch (InterruptedException | ExecutionException e) {
      logger.atSevere().withCause(e).log("Error while waiting for cleanup sub-tasks to finish");
    }

    if (failedProjects.get() == 0) {
      logger.atInfo().log("Successfully finished cleanup up changes index");
    } else {
      logger.atInfo().log(
          "Finished cleanup of changes index, failed to process %d projects", failedProjects.get());
    }
    return new Result(
        totalIndexDocuments, processedIndexDocuments.get(), deletedIndexDocuments.get());
  }

  private void cleanupForProject(Project.NameKey name, ChangeIndexer indexer) {
    logger.atInfo().log("Cleaning up index for changes in %s", name);
    Set<Change.Id> changesInIndex = queryChangesInIndex(name);
    try (Repository repo = repoManager.openRepository(name)) {
      ImmutableMap<Change.Id, ObjectId> changesInNoteDb = ChangeNotes.Factory.scanChangeIds(repo);
      changesInIndex.removeAll(changesInNoteDb.keySet());
      processedIndexDocuments.addAndGet(changesInNoteDb.size());
      if (!changesInIndex.isEmpty()) {
        logger.atFine().log(
            "Found changes in index which do not exist in noteDb: %s", changesInIndex);
        for (Change.Id id : changesInIndex) {
          logger.atFine().log("Deleting change %s from index", id);
          indexer.delete(id);
          deletedIndexDocuments.incrementAndGet();
          processedIndexDocuments.incrementAndGet();
          logProgress();
        }
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Couldn't cleanup index for changes in %s", name);
      failedProjects.incrementAndGet();
    } finally {
      processedProjects.incrementAndGet();
    }
  }

  private Set<Change.Id> queryChangesInIndex(Project.NameKey project) {
    InternalChangeQuery query = queryProvider.get();
    query.setRequestedFields(ChangeField.CHANGE_ID_SPEC);
    return new HashSet<>(query.byProject(project).stream().map(ChangeData::getId).toList());
  }

  private void logProgress() {
    logger.atInfo().atMostEvery(30, TimeUnit.SECONDS).log(
        "Processed (%d/%d) projects (%d failed), (%d/%d) index documents (%d deleted)",
        processedProjects.get(),
        totalProjects,
        failedProjects.get(),
        processedIndexDocuments.get(),
        totalIndexDocuments,
        deletedIndexDocuments.get());
  }
}
