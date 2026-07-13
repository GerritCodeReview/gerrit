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
import com.google.common.hash.Hashing;
import com.google.common.io.MoreFiles;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

/**
 * Manages the change-index write-ahead intent files under {@code $site_dir/data/pending-index/}.
 *
 * <p>Each intent is a file at {@code <data_dir>/<pid>_<start_time>/<threadId>/sha(project, change)}
 * with the JSON content of {@link Intent}.
 */
@Singleton
public final class PendingIndexUpdate {
  record Intent(String project, int changeId, String operation) {}

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String PROCESS_MARKER =
      ProcessHandle.current().pid() + "_" + ManagementFactory.getRuntimeMXBean().getStartTime();
  private static final Gson GSON = new Gson();
  private final Path intentDir;
  private final Path buildingDir;
  private final Path runningDir;
  private final ChangeIndexer indexer;

  @Inject
  public PendingIndexUpdate(SitePaths sitePaths, ChangeIndexer indexer) {
    intentDir = sitePaths.data_dir.resolve("pending-index");
    buildingDir = intentDir.resolve("building");
    runningDir = intentDir.resolve(PROCESS_MARKER);
    this.indexer = indexer;
  }

  /** Returns the per-thread intent directory for {@code threadId}. */
  public Path threadDir(long threadId) {
    return runningDir.resolve(String.valueOf(threadId));
  }

  public String filename(Project.NameKey project, Change.Id changeId) {
    return Hashing.sha256()
        .hashString("%s_%s".formatted(project, changeId), StandardCharsets.UTF_8)
        .toString();
  }

  public void cleanIfEmpty(Path dir) {
    try {
      Files.delete(dir);
    } catch (NoSuchFileException | DirectoryNotEmptyException ignored) {
      // Already gone or not empty.
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to delete directory %s", dir);
    }
  }

  /** Writes an intent file for the given change under the thread's pending directory. */
  public void write(long threadId, Project.NameKey project, Change.Id changeId, boolean delete)
      throws IOException {
    Files.createDirectories(buildingDir);
    Path tmp =
        Files.writeString(
            Files.createTempFile(buildingDir, null, null),
            GSON.toJson(new Intent(project.get(), changeId.get(), delete ? "delete" : "index")));

    Path dir = threadDir(threadId);
    Files.createDirectories(dir);
    Files.move(tmp, dir.resolve(filename(project, changeId)), StandardCopyOption.ATOMIC_MOVE);
  }

  /** Deletes the intent file for {@code changeId} under the thread's pending directory. */
  public void delete(long threadId, Project.NameKey project, Change.Id changeId) {
    try {
      Path threadDir = threadDir(threadId);
      Files.deleteIfExists(threadDir.resolve(filename(project, changeId)));
      cleanIfEmpty(threadDir);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to delete pending index intent for change %s in thread %d", changeId, threadId);
    }
  }

  /**
   * Reads the intent file for {@code changeId} in thread {@code threadId}, applies the index
   * operation, then deletes the file.
   */
  void recover(Path file) throws IOException {
    Intent intent;
    try {
      intent = GSON.fromJson(Files.readString(file), Intent.class);
    } catch (JsonSyntaxException e) {
      logger.atWarning().withCause(e).log(
          "Malformed pending index intent, deleting %s", file.getFileName());
      Files.deleteIfExists(file);
      return;
    }
    if (intent == null
        || intent.project() == null
        || intent.operation() == null
        || intent.changeId() <= 0) {
      logger.atWarning().log("Malformed pending index intent, deleting %s", file.getFileName());
      Files.deleteIfExists(file);
      return;
    }
    Project.NameKey project = Project.nameKey(intent.project());
    try {
      switch (intent.operation()) {
        case "delete" -> indexer.delete(project, Change.id(intent.changeId()));
        case "index" -> indexer.index(project, Change.id(intent.changeId()));
        default ->
            logger.atSevere().log(
                "Unknown operation '%s' in pending index intent: %s", intent.operation(), intent);
      }
    } catch (NoSuchChangeException e) {
      // Ignore silently., might already be deleted later.
    } catch (Exception e) {
      // catch all indexing exceptions to not propagate further.
      logger.atSevere().withCause(e).log("Exception while recovering index intent: %s", intent);
    }
    Files.deleteIfExists(file);
  }

  /** Background scanner that recovers change index updates missed due to a crash/interrupt. */
  @Singleton
  public final class Scanner implements Runnable, LifecycleListener {
    public static class Module extends LifecycleModule {
      @Override
      protected void configure() {
        listener().to(Scanner.class);
      }
    }

    private static final Duration DEFAULT_SCAN_INTERVAL = Duration.ofMinutes(5);
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

      // Remove the in-process intents from previous crash.
      try {
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
                    if (!Files.exists(intentDir)) {
                      // fresh install or feature newly enabled
                      return;
                    }

                    try (DirectoryStream<Path> pidDirs = Files.newDirectoryStream(intentDir)) {
                      for (Path pidDir : pidDirs) {
                        if (runningDir.equals(pidDir) || buildingDir.equals(pidDir)) {
                          continue;
                        }

                        processPidDir(pidDir, true);
                        cleanIfEmpty(pidDir);
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
        if (!Files.isDirectory(runningDir)) {
          // no intents written yet.
          return;
        }

        processPidDir(runningDir, false);
      } catch (Exception e) {
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
            recover(intent);
          } catch (IOException e) {
            logger.atWarning().withCause(e).log(
                "Failed to recover pending index intent %s", intent.getFileName());
          }
        }
      } catch (IOException e) {
        logger.atWarning().withCause(e).log(
            "Failed to recover pending index updates for %s", threadDir);
      }
      cleanIfEmpty(threadDir);
    }
  }
}
