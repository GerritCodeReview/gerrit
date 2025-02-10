// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.util;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.ByteStreams;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.LogConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/** Compresses and eventually deletes the old logs. */
public class LogFileManager implements Runnable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final Pattern LOG_FILENAME_PATTERN =
      Pattern.compile("^.+(?<date>\\d{4}-\\d{2}-\\d{2})(.gz)?");
  protected final boolean compressionEnabled;
  private final Duration timeToKeep;

  public static class LogFileManagerModule extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final LogFileManager manager;

    @Inject
    Lifecycle(WorkQueue queue, LogFileManager manager) {
      this.queue = queue;
      this.manager = manager;
    }

    @Override
    public void start() {
      if (!manager.compressionEnabled && manager.timeToKeep.isNegative()) {
        return;
      }
      // compress log once and then schedule compression every day at 11:00pm
      queue.getDefaultQueue().execute(manager);
      ZoneId zone = ZoneId.systemDefault();
      LocalDateTime now = LocalDateTime.now(zone);
      long milliSecondsUntil11pm =
          now.until(now.withHour(23).withMinute(0).withSecond(0).withNano(0), ChronoUnit.MILLIS);
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          queue
              .getDefaultQueue()
              .scheduleAtFixedRate(
                  manager, milliSecondsUntil11pm, HOURS.toMillis(24), MILLISECONDS);
    }

    @Override
    public void stop() {}
  }

  private final Path logs_dir;

  @Inject
  LogFileManager(SitePaths site, LogConfig config) {
    this.logs_dir = resolve(site.logs_dir);
    this.compressionEnabled = config.shouldCompress();
    this.timeToKeep = config.getTimeToKeep();
  }

  private static Path resolve(Path p) {
    try {
      return p.toRealPath().normalize();
    } catch (IOException e) {
      return p.toAbsolutePath().normalize();
    }
  }

  @Override
  public void run() {
    logger.atInfo().log("Starting log file maintenance.");
    try {
      if (!Files.isDirectory(logs_dir)) {
        return;
      }
      try (DirectoryStream<Path> list = Files.newDirectoryStream(logs_dir)) {
        for (Path entry : list) {
          if (isLive(entry) || !isLogFile(entry)) {
            continue;
          }
          if (!timeToKeep.isNegative() && isExpired(entry)) {
            if (delete(entry)) {
              continue;
            }
          }
          if (compressionEnabled && !isCompressed(entry)) {
            compress(entry);
          }
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Error listing logs to compress in %s", logs_dir);
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Failed to process log files: %s", e.getMessage());
    }
    logger.atInfo().log("Log file maintenance has finished.");
  }

  private boolean isLive(Path entry) {
    String name = entry.getFileName().toString();
    return name.endsWith("_log")
        || name.endsWith(".log")
        || name.endsWith(".run")
        || name.endsWith(".pid")
        || name.endsWith(".json");
  }

  private boolean isCompressed(Path entry) {
    String name = entry.getFileName().toString();
    return name.endsWith(".gz") //
        || name.endsWith(".zip") //
        || name.endsWith(".bz2");
  }

  private boolean isLogFile(Path entry) {
    return Files.isRegularFile(entry);
  }

  @VisibleForTesting
  boolean isExpired(Path entry) {
    try {
      FileTime creationTime = Files.readAttributes(entry, BasicFileAttributes.class).creationTime();

      if (creationTime.toInstant().equals(Instant.EPOCH)) {
        Optional<Instant> fileDate = getDateFromFilename(entry);
        if (fileDate.isPresent()) {
          return fileDate.get().isBefore(Instant.now().minus(timeToKeep));
        }
        return false;
      }

      return creationTime.toInstant().isBefore(Instant.now().minus(timeToKeep));
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Failed to get creation time of log file %s", entry);
    }
    return false;
  }

  @VisibleForTesting
  Optional<Instant> getDateFromFilename(Path entry) {
    Matcher filenameMatcher = LOG_FILENAME_PATTERN.matcher(entry.getFileName().toString());
    if (filenameMatcher.matches()) {
      String rotationDate = filenameMatcher.group("date");
      if (rotationDate != null && !rotationDate.isBlank()) {
        return Optional.of(Instant.parse(rotationDate + "T00:00:00.00Z"));
      }
    }
    return Optional.empty();
  }

  private boolean delete(Path entry) {
    try {
      Files.deleteIfExists(entry);
      logger.atInfo().log("Log file %s has been deleted.", entry);
      return true;
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to delete log file %s", entry);
    }
    return false;
  }

  private void compress(Path src) {
    Path dst = src.resolveSibling(src.getFileName() + ".gz");
    Path tmp = src.resolveSibling(".tmp." + src.getFileName());
    try {
      try (InputStream in = Files.newInputStream(src);
          OutputStream out = new GZIPOutputStream(Files.newOutputStream(tmp))) {
        ByteStreams.copy(in, out);
      }
      tmp.toFile().setReadOnly();
      try {
        Files.move(tmp, dst);
      } catch (IOException e) {
        throw new IOException("Cannot rename " + tmp + " to " + dst, e);
      }
      Files.delete(src);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot compress %s", src);
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException e2) {
        logger.atWarning().withCause(e2).log("Failed to delete temporary log file %s", tmp);
      }
    }
  }

  @Override
  public String toString() {
    return "Log File Manager";
  }
}
