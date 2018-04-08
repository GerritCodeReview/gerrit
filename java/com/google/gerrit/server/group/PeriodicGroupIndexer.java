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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.ScheduleConfig;
import com.google.gerrit.config.ScheduleConfig.Schedule;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger log = LoggerFactory.getLogger(PeriodicGroupIndexer.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  private static class Lifecycle implements LifecycleListener {
    private final Config cfg;
    private final WorkQueue queue;
    private final PeriodicGroupIndexer runner;

    @Inject
    Lifecycle(@GerritServerConfig Config cfg, WorkQueue queue, PeriodicGroupIndexer runner) {
      this.cfg = cfg;
      this.queue = queue;
      this.runner = runner;
    }

    @Override
    public void start() {
      boolean runOnStartup = cfg.getBoolean("index", "scheduledIndexer", "runOnStartup", true);
      if (runOnStartup) {
        runner.run();
      }

      boolean isEnabled = cfg.getBoolean("index", "scheduledIndexer", "enabled", true);
      if (!isEnabled) {
        log.warn("index.scheduledIndexer is disabled");
        return;
      }

      Schedule schedule =
          ScheduleConfig.builder(cfg, "index")
              .setSubsection("scheduledIndexer")
              .buildSchedule()
              .orElseGet(() -> Schedule.createOrFail(TimeUnit.MINUTES.toMillis(5), "00:00"));
      queue.scheduleAtFixedRate(runner, schedule);
    }

    @Override
    public void stop() {
      // handled by WorkQueue.stop() already
    }
  }

  private final AllUsersName allUsersName;
  private final GitRepositoryManager repoManager;
  private final Provider<GroupIndexer> groupIndexerProvider;

  private ImmutableSet<AccountGroup.UUID> groupUuids;

  @Inject
  PeriodicGroupIndexer(
      AllUsersName allUsersName,
      GitRepositoryManager repoManager,
      Provider<GroupIndexer> groupIndexerProvider) {
    this.allUsersName = allUsersName;
    this.repoManager = repoManager;
    this.groupIndexerProvider = groupIndexerProvider;
  }

  @Override
  public synchronized void run() {
    try (Repository allUsers = repoManager.openRepository(allUsersName)) {
      ImmutableSet<AccountGroup.UUID> newGroupUuids =
          GroupNameNotes.loadAllGroups(allUsers)
              .stream()
              .map(GroupReference::getUUID)
              .collect(toImmutableSet());
      GroupIndexer groupIndexer = groupIndexerProvider.get();
      int reindexCounter = 0;
      for (AccountGroup.UUID groupUuid : newGroupUuids) {
        if (groupIndexer.reindexIfStale(groupUuid)) {
          reindexCounter++;
        }
      }
      if (groupUuids != null) {
        // Check if any group was deleted since the last run and if yes remove these groups from the
        // index.
        for (AccountGroup.UUID groupUuid : Sets.difference(groupUuids, newGroupUuids)) {
          groupIndexer.index(groupUuid);
          reindexCounter++;
        }
      }
      groupUuids = newGroupUuids;
      log.info("Run group indexer, {} groups reindexed", reindexCounter);
    } catch (Throwable t) {
      log.error("Failed to reindex groups", t);
    }
  }
}
