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
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.jgit.lib.Config;

/**
 * Manages the change-index write-ahead intent files under {@code $site_dir/data/pending-index/}.
 *
 * <p>Each intent is a file at {@code <data_dir>/<pid>_<start_time>/<transactionId>/sha(project,
 * change)} with the JSON content of {@link Intent}.
 */
@Singleton
public final class PendingIndexUpdate {
  record Intent(String project, int changeId, String operation) {}

  public static final String LOCK_SUFFIX = ".lck";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String PROCESS_MARKER =
      ProcessHandle.current().pid() + "_" + ManagementFactory.getRuntimeMXBean().getStartTime();
  private static final Gson GSON = new Gson();
  private final ChangeIndexer indexer;
  private final boolean enabled;
  private final AtomicLong transactionCounter = new AtomicLong();
  private final Set<Long> liveTransactions = ConcurrentHashMap.newKeySet();
  final Path intentDir;
  final Path buildingDir;
  final Path runningDir;

  @Inject
  public PendingIndexUpdate(
      SitePaths sitePaths, ChangeIndexer indexer, @GerritServerConfig Config cfg) {
    intentDir = sitePaths.data_dir.resolve("pending-index");
    buildingDir = intentDir.resolve("building");
    runningDir = intentDir.resolve(PROCESS_MARKER);
    this.indexer = indexer;
    this.enabled = cfg.getBoolean("index", null, "staleChangeRecovery", false);
  }

  /** Returns {@code true} if stale change recovery is active for this process. */
  public boolean isEnabled() {
    return enabled;
  }

  /** Registers a new in-flight update and returns its ID. */
  public long initiate() {
    long id = transactionCounter.incrementAndGet();
    liveTransactions.add(id);
    return id;
  }

  public void markFinished(long transactionId) {
    liveTransactions.remove(transactionId);
  }

  /** Returns {@code true} if the sequence is still in-flight in this process. */
  public boolean isLive(long transactionId) {
    return liveTransactions.contains(transactionId);
  }

  /** Returns the per-transaction intent directory for {@code transactionId}. */
  public Path transactionDir(long transactionId) {
    return runningDir.resolve(String.valueOf(transactionId));
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

  /** Writes an intent file for the given change under the transaction's pending directory. */
  public void write(long transactionId, Project.NameKey project, Change.Id changeId, boolean delete)
      throws IOException {
    Files.createDirectories(buildingDir);
    Path tmp =
        Files.writeString(
            Files.createTempFile(buildingDir, null, null),
            GSON.toJson(new Intent(project.get(), changeId.get(), delete ? "delete" : "index")));

    Path dir = transactionDir(transactionId);
    Files.createDirectories(dir);
    Files.move(tmp, dir.resolve(filename(project, changeId)), StandardCopyOption.ATOMIC_MOVE);
  }

  /** Deletes the intent file for {@code changeId} under the transaction's pending directory. */
  public void delete(long transactionId, Project.NameKey project, Change.Id changeId) {
    try {
      Path transactionDir = transactionDir(transactionId);
      Files.deleteIfExists(transactionDir.resolve(filename(project, changeId)));
      cleanIfEmpty(transactionDir);
    } catch (IOException e) {
      logger.atWarning().withCause(e).log(
          "Failed to delete pending index intent for change %s in transaction %d",
          changeId, transactionId);
    }
  }

  /**
   * Applies the index operation described by the intent file, then schedules the file for deletion
   * once the write has been durably committed to disk.
   *
   * @param skipChecks if true, skips the lock-file check (used during startup recovery where all
   *     previous lock files are stale)
   */
  void recover(Path file, boolean skipChecks) throws IOException {
    Path lock = file.resolveSibling(file.getFileName() + LOCK_SUFFIX);
    if (!skipChecks && lock.toFile().exists()) {
      return; // already submitted — awaiting flush
    }

    Intent intent;
    try {
      intent = GSON.fromJson(Files.readString(file), Intent.class);
    } catch (JsonSyntaxException e) {
      logger.atWarning().withCause(e).log(
          "Malformed pending index intent, deleting %s", file.getFileName());
      Files.deleteIfExists(file);
      Files.deleteIfExists(lock);
      cleanIfEmpty(file.getParent());
      return;
    }
    if (intent == null
        || intent.project() == null
        || intent.operation() == null
        || intent.changeId() <= 0) {
      logger.atWarning().log("Malformed pending index intent, deleting %s", file.getFileName());
      Files.deleteIfExists(file);
      Files.deleteIfExists(lock);
      cleanIfEmpty(file.getParent());
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
      // Ignore silently. change got deleted after intent.
    } catch (Exception e) {
      // catch all indexing exceptions to not propagate further.
      logger.atSevere().withCause(e).log("Exception while recovering index intent: %s", intent);
    }

    // Defer deletion until index update is durably committed to disk.
    indexer.schedulePostFlush(
        () -> {
          try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(lock);
            cleanIfEmpty(file.getParent());
          } catch (IOException e) {
            logger.atWarning().withCause(e).log(
                "Failed to delete recovered intent file %s", file.getFileName());
          }
        });
    var unused = lock.toFile().createNewFile();
  }
}
