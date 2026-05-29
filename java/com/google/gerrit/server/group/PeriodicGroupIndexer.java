// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.git.QueueProvider.QueueType.BATCH;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.Repository;

/**
 * Runnable to schedule periodic group reindexing.
 *
 * <p>Periodic group indexing is intended to run only on slaves. Replication to slaves happens on
 * Git level so that Gerrit is not aware of incoming replication events. But slaves need an updated
 * group index to resolve memberships of users for ACL validation. To keep the group index in slaves
 * up-to-date this class periodically scans the group refs in the All-Users repository to reindex
 * groups if they are stale. The ref states of the group refs are cached so that on each run deleted
 * groups can be detected and reindexed. This means callers of slaves may observe outdated group
 * information until the next indexing happens. The interval on which group indexing is done is
 * configurable by setting {@code index.scheduledIndexer.interval} in {@code gerrit.config}. By
 * default group indexing is done every 5 minutes.
 */
public class PeriodicGroupIndexer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final GroupIndexCollection indexes;
  private final Provider<GroupIndexer> groupIndexerProvider;
  private final IndexConfig indexConfig;

  @Inject
  PeriodicGroupIndexer(
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      GroupIndexCollection indexes,
      IndexConfig indexConfig,
      Provider<GroupIndexer> groupIndexerProvider) {
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.executor = executor;
    this.indexes = indexes;
    this.indexConfig = indexConfig;
    this.groupIndexerProvider = groupIndexerProvider;
  }

  @Override
  public synchronized void run() {
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      ImmutableSet<AccountGroup.UUID> allGroups =
          GroupNameNotes.loadAllGroups(allUsers).stream()
              .map(GroupReference::getUUID)
              .collect(toImmutableSet());
      GroupIndexer groupIndexer = groupIndexerProvider.get();
      AtomicInteger reindexCounter = new AtomicInteger();
      List<ListenableFuture<?>> indexingTasks = new ArrayList<>();
      for (AccountGroup.UUID groupUuid : allGroups) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  if (groupIndexer.reindexIfStale(groupUuid)) {
                    reindexCounter.incrementAndGet();
                  }
                }));
      }

      Set<AccountGroup.UUID> groupsInIndex = queryAllGroupsFromIndex();
      for (AccountGroup.UUID groupUuid : Sets.difference(groupsInIndex, allGroups)) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  groupIndexer.index(groupUuid);
                  reindexCounter.incrementAndGet();
                }));
      }
      Futures.successfulAsList(indexingTasks).get();
      logger.atInfo().log("Run group indexer, %s groups reindexed", reindexCounter);
    } catch (Exception t) {
      logger.atSevere().withCause(t).log("Failed to reindex groups");
    }
  }

  private Set<AccountGroup.UUID> queryAllGroupsFromIndex() {
    try {
      DataSource<InternalGroup> result =
          indexes
              .getSearchIndex()
              .getSource(
                  Predicate.any(),
                  QueryOptions.create(
                      indexConfig, 0, Integer.MAX_VALUE, Set.of(GroupField.UUID_FIELD.name())));
      return StreamSupport.stream(result.readRaw().spliterator(), false)
          .map(f -> fromUUIDField(f))
          .collect(Collectors.toUnmodifiableSet());
    } catch (QueryParseException e) {
      throw new RuntimeException(e);
    }
  }

  private static AccountGroup.UUID fromUUIDField(FieldBundle f) {
    return AccountGroup.uuid(f.<String>getValue(GroupField.UUID_FIELD_SPEC));
  }
}
