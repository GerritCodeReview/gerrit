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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Manages the change-index write-ahead intent files under {@code $site_dir/index/pending/}.
 *
 * <p>Each intent is a file at {@code pending/<threadId>/<changeId>} with content {@code
 * "<project>\t<changeId>\t<operation>"}.
 */
@Singleton
public final class PendingIndexUpdate {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public final Path pendingDir;
  private final ChangeIndexer indexer;

  @Inject
  public PendingIndexUpdate(SitePaths sitePaths, ChangeIndexer indexer) {
    this.pendingDir = sitePaths.index_dir.resolve("pending");
    this.indexer = indexer;
  }

  /** Returns the per-thread intent directory for {@code threadId}. */
  public Path threadDir(long threadId) {
    return pendingDir.resolve(String.valueOf(threadId));
  }

  /** Writes an intent file for the given change under the thread's pending directory. */
  public void write(long threadId, Project.NameKey project, Change.Id changeId, boolean delete) {
    try {
      Path dir = threadDir(threadId);
      Files.createDirectories(dir);
      Files.writeString(
          dir.resolve(String.valueOf(changeId.get())),
          project.get() + "\t" + changeId.get() + "\t" + (delete ? "delete" : "index"));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to write index intent for change %s in thread %d", changeId, threadId);
    }
  }

  /** Deletes the intent file for {@code changeId} under the thread's pending directory. */
  public void delete(long threadId, Change.Id changeId) {
    try {
      Files.deleteIfExists(threadDir(threadId).resolve(String.valueOf(changeId.get())));
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to delete pending index intent for change %s in thread %d", changeId, threadId);
    }
  }

  /** Removes the thread's pending directory if empty; no-op if intents remain. */
  public void cleanupThreadDir(long threadId) {
    try {
      Files.deleteIfExists(threadDir(threadId));
    } catch (DirectoryNotEmptyException ignored) {
      // other in-flight intents remain; directory will be cleaned up on next successful run
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to clean up pending index directory for thread %d", threadId);
    }
  }

  /**
   * Reads the intent file for {@code changeId} in thread {@code threadId}, applies the index
   * operation, then deletes the file.
   */
  void apply(long threadId, Change.Id changeId) throws IOException {
    Path file = threadDir(threadId).resolve(String.valueOf(changeId.get()));
    String[] parts = Files.readString(file).split("\t", 3);
    if (parts.length != 3) {
      logger.atWarning().log(
          "Malformed pending index intent for change %s in thread %d, skipping",
          changeId, threadId);
      return;
    }
    Project.NameKey project = Project.nameKey(parts[0]);
    if (parts[2].equals("delete")) {
      indexer.delete(project, changeId);
    } else {
      indexer.index(project, changeId);
    }
    Files.deleteIfExists(file);
  }

  /** Background scanner that recovers change index updates missed due to a crash/interrupt. */
  @Singleton
  public final class Scanner implements Runnable, LifecycleListener {
    private static final Duration DEFAULT_SCAN_INTERVAL = Duration.ofMinutes(5);

    public static class Module extends LifecycleModule {
      @Override
      protected void configure() {
        listener().to(Scanner.class);
      }
    }

    private final WorkQueue workQueue;
    private final Config cfg;
    private final Duration scanInterval;

    @Inject
    Scanner(WorkQueue workQueue, @GerritServerConfig Config cfg) {
      this.workQueue = workQueue;
      this.cfg = cfg;
      this.scanInterval =
          Duration.ofMillis(
              cfg.getTimeUnit(
                  "index",
                  null,
                  "staleChangeRecoveryInterval",
                  DEFAULT_SCAN_INTERVAL.toMillis(),
                  TimeUnit.MILLISECONDS));
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
                  this, scanInterval.toMillis(), scanInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {}

    @Override
    public synchronized void run() {
      if (!Files.isDirectory(pendingDir)) {
        return;
      }
      try (DirectoryStream<Path> threadDirs = Files.newDirectoryStream(pendingDir)) {
        for (Path threadDir : threadDirs) {
          long threadId;
          try {
            threadId = Long.parseLong(threadDir.getFileName().toString());
          } catch (NumberFormatException e) {
            logger.atWarning().log(
                "Unexpected entry in pending index dir: %s; skipping", threadDir.getFileName());
            continue;
          }
          if (isThreadAlive(threadId)) {
            continue;
          }
          processDeadThread(threadId);
        }
      } catch (Exception e) {
        logger.atSevere().withCause(e).log("Failed to run pending index update scan");
      }
    }

    private static boolean isThreadAlive(long threadId) {
      return ManagementFactory.getThreadMXBean().getThreadInfo(threadId) != null;
    }

    private void processDeadThread(long threadId) throws IOException {
      try (DirectoryStream<Path> changes = Files.newDirectoryStream(threadDir(threadId))) {
        for (Path change : changes) {
          Change.Id changeId = Change.id(Integer.parseInt(change.getFileName().toString()));
          apply(threadId, changeId);
        }
      }
      cleanupThreadDir(threadId);
    }
  }
}
