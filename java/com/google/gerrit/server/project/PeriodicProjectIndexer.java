// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Project;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.project.ProjectData;
import com.google.gerrit.index.project.ProjectField;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class PeriodicProjectIndexer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager gitRepoManager;
  private final ProjectIndexer indexer;
  private ListeningExecutorService executor;
  private final ProjectIndexCollection indexes;
  private final IndexConfig indexConfig;

  @Inject
  PeriodicProjectIndexer(
      GitRepositoryManager gitRepoManager,
      ProjectIndexer indexer,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      ProjectIndexCollection indexes,
      IndexConfig indexConfig) {
    this.gitRepoManager = gitRepoManager;
    this.indexer = indexer;
    this.executor = executor;
    this.indexes = indexes;
    this.indexConfig = indexConfig;
  }

  @Override
  public void run() {
    logger.atInfo().log("reindexing projects");
    Set<Project.NameKey> gitRepos = gitRepoManager.list();
    List<ListenableFuture<?>> indexingTasks = new ArrayList<>();
    for (Project.NameKey n : gitRepos) {
      indexingTasks.add(executor.submit(() -> indexer.index(n)));
    }
    try {
      Futures.successfulAsList(indexingTasks).get();
    } catch (InterruptedException | ExecutionException e) {
      logger.atSevere().log("Error while reindexing projects");
      return;
    }

    Set<Project.NameKey> projectsInIndex;
    try {
      DataSource<ProjectData> result =
          indexes
              .getSearchIndex()
              .getSource(
                  Predicate.any(),
                  QueryOptions.create(
                      indexConfig, 0, Integer.MAX_VALUE, Set.of(ProjectField.NAME_FIELD.name())));
      projectsInIndex =
          StreamSupport.stream(result.readRaw().spliterator(), false)
              .map(f -> fromIdField(f))
              .collect(Collectors.toUnmodifiableSet());
    } catch (QueryParseException e) {
      throw new RuntimeException(e);
    }

    for (Project.NameKey n : Sets.difference(projectsInIndex, gitRepos)) {
      logger.atInfo().log("removing non-existing project %s from index", n);
      indexer.index(n);
    }
  }

  private static Project.NameKey fromIdField(FieldBundle f) {
    return Project.nameKey(f.<String>getValue(ProjectField.NAME_SPEC));
  }
}
