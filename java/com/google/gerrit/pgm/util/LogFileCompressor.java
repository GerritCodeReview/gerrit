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

import com.google.common.io.ByteStreams;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Future;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Compresses the old error logs. */
public class LogFileCompressor implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(LogFileCompressor.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final LogFileCompressor compressor;
    private final boolean enabled;

    @Inject
    Lifecycle(WorkQueue queue, LogFileCompressor compressor, @GerritServerConfig Config config) {
      this.queue = queue;
      this.compressor = compressor;
      this.enabled = config.getBoolean("log", "compress", true);
    }

    @Override
    public void start() {
      if (!enabled) {
        return;
      }
      //compress log once and then schedule compression every day at 11:00pm
      queue.getDefaultQueue().execute(compressor);
      ZoneId zone = ZoneId.systemDefault();
      LocalDateTime now = LocalDateTime.now(zone);
      long milliSecondsUntil11pm =
          now.until(now.withHour(23).withMinute(0).withSecond(0).withNano(0), ChronoUnit.MILLIS);
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          queue
              .getDefaultQueue()
              .scheduleAtFixedRate(
                  compressor, milliSecondsUntil11pm, HOURS.toMillis(24), MILLISECONDS);
    }

    @Override
    public void stop() {}
  }

  private final Path logs_dir;

  @Inject
  LogFileCompressor(SitePaths site) {
    logs_dir = resolve(site.logs_dir);
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
    try {
      if (!Files.isDirectory(logs_dir)) {
        return;
      }
      try (DirectoryStream<Path> list = Files.newDirectoryStream(logs_dir)) {
        for (Path entry : list) {
          if (!isLive(entry) && !isCompressed(entry) && isLogFile(entry)) {
            compress(entry);
          }
        }
      } catch (IOException e) {
        log.error("Error listing logs to compress in " + logs_dir, e);
      }
    } catch (Exception e) {
      log.error("Failed to compress log files: " + e.getMessage(), e);
    }
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
      log.error("Cannot compress " + src, e);
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException e2) {
        log.warn("Failed to delete temporary log file " + tmp, e2);
      }
    }
  }

  @Override
  public String toString() {
    return "Log File Compressor";
  }
}
