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
import com.google.common.io.MoreFiles;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/** Background scanner that recovers change index updates missed due to a crash/interrupt. */
@Singleton
public final class PendingIndexUpdateScanner implements Runnable, LifecycleListener {
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(PendingIndexUpdateScanner.class);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Duration DEFAULT_SCAN_INTERVAL = Duration.ofMinutes(5);

  private final PendingIndexUpdate pendingIndexUpdate;
  private final WorkQueue workQueue;
  private final Duration scanInterval;

  @Inject
  PendingIndexUpdateScanner(
      PendingIndexUpdate pendingIndexUpdate, WorkQueue workQueue, @GerritServerConfig Config cfg) {
    this.pendingIndexUpdate = pendingIndexUpdate;
    this.workQueue = workQueue;
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
    if (!pendingIndexUpdate.isEnabled()) {
      return;
    }

    // Remove the in-process intents from previous crash.
    try {
      Path buildingDir = pendingIndexUpdate.buildingDir;
      if (Files.exists(buildingDir)) {
        MoreFiles.deleteRecursively(buildingDir);
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Unable to clean up building index directory");
    }

    // recover intents from previous crash.
    var unused =
        workQueue
            .getDefaultQueue()
            .submit(
                () -> {
                  Path intentDir = pendingIndexUpdate.intentDir;
                  if (!Files.exists(intentDir)) {
                    // fresh install or feature newly enabled
                    return;
                  }

                  try (DirectoryStream<Path> pidDirs = Files.newDirectoryStream(intentDir)) {
                    for (Path pidDir : pidDirs) {
                      if (pendingIndexUpdate.runningDir.equals(pidDir)
                          || pendingIndexUpdate.buildingDir.equals(pidDir)) {
                        continue;
                      }

                      processPidDir(pidDir, true);
                      pendingIndexUpdate.cleanIfEmpty(pidDir);
                    }
                  } catch (Exception e) {
                    logger.atSevere().withCause(e).log(
                        "Unable to recover index intents from previous run");
                  }
                });

    unused =
        workQueue
            .getDefaultQueue()
            .scheduleWithFixedDelay(
                this, scanInterval.toMillis(), scanInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {}

  @Override
  public void run() {
    try {
      Path runningDir = pendingIndexUpdate.runningDir;
      if (!Files.isDirectory(runningDir)) {
        // no intents written yet.
        return;
      }

      processPidDir(runningDir, false);
    } catch (RuntimeException e) {
      // catch all to not disrupt next run.
      logger.atSevere().withCause(e).log("Error in pending index intent run");
    }
  }

  private void processPidDir(Path pidDir, boolean skipDeadCheck) {
    try (DirectoryStream<Path> threadDirs = Files.newDirectoryStream(pidDir)) {
      for (Path threadDir : threadDirs) {
        long threadId;
        try {
          threadId = Long.parseLong(threadDir.getFileName().toString());
        } catch (NumberFormatException e) {
          logger.atWarning().log(
              "Unexpected entry in pending index dir: %s; skipping", threadDir.getFileName());
          MoreFiles.deleteRecursively(threadDir);
          continue;
        }
        if (skipDeadCheck || isThreadDead(threadId)) {
          processDeadThreadDir(threadDir);
        }
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to run pending index update scan");
    }
  }

  private static boolean isThreadDead(long threadId) {
    return ManagementFactory.getThreadMXBean().getThreadInfo(threadId) == null;
  }

  private void processDeadThreadDir(Path threadDir) {
    try (DirectoryStream<Path> intents = Files.newDirectoryStream(threadDir)) {
      for (Path intent : intents) {
        try {
          pendingIndexUpdate.recover(intent);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "Failed to recover pending index intent %s", intent.getFileName());
        }
      }
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to recover pending index updates for %s", threadDir);
    }
    pendingIndexUpdate.cleanIfEmpty(threadDir);
  }
}
