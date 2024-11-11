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
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
 *
 * <p>This class is not able to detect group deletions that were replicated while the slave was
 * offline. This means if group refs are deleted while the slave is offline these groups are not
 * removed from the group index when the slave is started. However since group deletion is not
 * supported this should never happen and one can always do an offline reindex before starting the
 * slave.
 */
public class PeriodicGroupIndexer implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final ListeningExecutorService executor;
  private final Provider<GroupIndexer> groupIndexerProvider;

  private ImmutableSet<AccountGroup.UUID> groupUuids;

  @Inject
  PeriodicGroupIndexer(
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      @IndexExecutor(BATCH) ListeningExecutorService executor,
      Provider<GroupIndexer> groupIndexerProvider) {
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.executor = executor;
    this.groupIndexerProvider = groupIndexerProvider;
  }

  @Override
  public synchronized void run() {
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      ImmutableSet<AccountGroup.UUID> newGroupUuids =
          GroupNameNotes.loadAllGroups(allUsers).stream()
              .map(GroupReference::getUUID)
              .collect(toImmutableSet());
      GroupIndexer groupIndexer = groupIndexerProvider.get();
      AtomicInteger reindexCounter = new AtomicInteger();
      List<ListenableFuture<?>> indexingTasks = new ArrayList<>();
      for (AccountGroup.UUID groupUuid : newGroupUuids) {
        indexingTasks.add(
            executor.submit(
                () -> {
                  if (groupIndexer.reindexIfStale(groupUuid)) {
                    reindexCounter.incrementAndGet();
                  }
                }));
      }
      if (groupUuids != null) {
        // Check if any group was deleted since the last run and if yes remove these groups from the
        // index.
        for (AccountGroup.UUID groupUuid : Sets.difference(groupUuids, newGroupUuids)) {
          indexingTasks.add(
              executor.submit(
                  () -> {
                    groupIndexer.index(groupUuid);
                    reindexCounter.incrementAndGet();
                  }));
        }
      }
      Futures.successfulAsList(indexingTasks).get();
      groupUuids = newGroupUuids;
      logger.atInfo().log("Run group indexer, %s groups reindexed", reindexCounter);
    } catch (Exception t) {
      logger.atSevere().withCause(t).log("Failed to reindex groups");
    }
  }
}
