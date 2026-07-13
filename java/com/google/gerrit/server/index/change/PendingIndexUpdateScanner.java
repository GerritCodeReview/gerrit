// Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

/** Background scanner that recovers stuck index updates. */
@Singleton
public class PendingIndexUpdateScanner implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration DEFAULT_SCAN_INTERVAL = Duration.ofMinutes(5);
  private static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMinutes(1);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      bind(PendingIndexUpdateScanner.class);
      listener().to(Scheduler.class);
    }
  }

  /** Schedules {@link PendingIndexUpdateScanner} on startup. */
  @Singleton
  static class Scheduler implements LifecycleListener {
    private final WorkQueue workQueue;
    private final PendingIndexUpdateScanner scanner;
    private final Config cfg;

    @Inject
    Scheduler(
        WorkQueue workQueue, PendingIndexUpdateScanner scanner, @GerritServerConfig Config cfg) {
      this.workQueue = workQueue;
      this.scanner = scanner;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      if (!cfg.getBoolean("index", null, "staleChangeRecovery", false)) {
        return;
      }
      @SuppressWarnings("unused")
      var unused =
          workQueue
              .getDefaultQueue()
              .scheduleWithFixedDelay(
                  scanner,
                  scanner.gracePeriod.toMillis(),
                  scanner.scanInterval.toMillis(),
                  TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {}
  }

  private final AllProjectsName allProjectsName;
  private final GitRepositoryManager repoManager;
  private final ChangeIndexer indexer;
  private final Duration scanInterval;
  private final Duration gracePeriod;

  @Inject
  PendingIndexUpdateScanner(
      AllProjectsName allProjectsName,
      GitRepositoryManager repoManager,
      ChangeIndexer indexer,
      @GerritServerConfig Config cfg) {
    this.allProjectsName = allProjectsName;
    this.repoManager = repoManager;
    this.indexer = indexer;
    this.scanInterval =
        Duration.ofMillis(
            cfg.getTimeUnit(
                "index",
                null,
                "staleChangeRecoveryInterval",
                DEFAULT_SCAN_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS));
    this.gracePeriod =
        Duration.ofMillis(
            cfg.getTimeUnit(
                "index",
                null,
                "staleChangeRecoveryGracePeriod",
                DEFAULT_GRACE_PERIOD.toMillis(),
                TimeUnit.MILLISECONDS));
  }

  @Override
  public synchronized void run() {
    try (Repository allProjects = repoManager.openRepository(allProjectsName);
        ObjectReader reader = allProjects.newObjectReader()) {

      List<Ref> pendingRefs =
          allProjects.getRefDatabase().getRefsByPrefix(RefNames.REFS_PENDING_INDEX);
      if (pendingRefs.isEmpty()) {
        return;
      }
      logger.atFine().log(
          "Found %d pending index intent(s) in All-Projects; recovering", pendingRefs.size());

      long now = System.currentTimeMillis();
      List<ListenableFuture<?>> futures = new ArrayList<>(pendingRefs.size());
      List<Ref> submitted = new ArrayList<>(pendingRefs.size());
      for (Ref ref : pendingRefs) {
        ListenableFuture<?> f = submitIndexTask(reader, ref, now);
        if (f != null) {
          futures.add(f);
          submitted.add(ref);
        }
      }
      Futures.successfulAsList(futures).get();
      List<Ref> succeeded = new ArrayList<>();
      for (int i = 0; i < futures.size(); i++) {
        try {
          futures.get(i).get();
          succeeded.add(submitted.get(i));
        } catch (ExecutionException e) {
          logger.atWarning().withCause(e).log(
              "Failed to recover pending index intent at %s; will retry on next scan",
              submitted.get(i).getName());
        }
      }

      removePendingIntentRefs(allProjects, succeeded);
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Failed to run pending index update scan");
    }
  }

  /**
   * Parses the intent blob at {@code ref} and submits the appropriate index task.
   *
   * <p>Blob format: {@code "<project>\t<changeId>\t<operation>\t<timestampMillis>"}.
   *
   * <p>Returns {@code null} if the intent is within the grace period.
   */
  @Nullable
  private ListenableFuture<?> submitIndexTask(ObjectReader reader, Ref ref, long now)
      throws IOException {
    byte[] bytes = reader.open(ref.getObjectId(), Constants.OBJ_BLOB).getBytes();
    String[] parts = new String(bytes, StandardCharsets.UTF_8).split("\t", 4);
    if (parts.length != 4) {
      logger.atWarning().log(
          "Malformed pending index intent at %s (expected project\\tchangeId\\toperation\\tts),"
              + " skipping",
          ref.getName());
      return null;
    }

    long writtenAt = Long.parseLong(parts[3]);
    if (now - writtenAt < gracePeriod.toMillis()) {
      return null;
    }

    Project.NameKey project = Project.nameKey(parts[0]);
    Change.Id changeId = Change.id(Integer.parseInt(parts[1]));
    boolean isDelete = parts[2].equals("delete");

    return isDelete
        ? indexer.deleteAsync(project, changeId)
        : indexer.indexAsync(project, changeId);
  }

  private static void removePendingIntentRefs(Repository allProjects, List<Ref> refs)
      throws IOException {
    if (refs.isEmpty()) {
      return;
    }
    try (RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.CHANGE_MODIFICATION)) {
      BatchRefUpdate bru = allProjects.getRefDatabase().newBatchUpdate();
      bru.setAtomic(false);
      for (Ref ref : refs) {
        bru.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
      }
      try (RevWalk rw = new RevWalk(allProjects)) {
        bru.execute(rw, NullProgressMonitor.INSTANCE);
      }
    }
  }
}
