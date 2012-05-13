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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/** Compresses the old error logs. */
public class LogFileCompressor implements Runnable {
  private static final Logger log =
      LoggerFactory.getLogger(LogFileCompressor.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final LogFileCompressor compresser;

    @Inject
    Lifecycle(final WorkQueue queue, final LogFileCompressor compressor) {
      this.queue = queue;
      this.compresser = compressor;
    }

    @Override
    public void start() {
      queue.getDefaultQueue().scheduleWithFixedDelay(compresser, 1, 24, HOURS);
    }

    @Override
    public void stop() {
    }
  }

  private final File logs_dir;

  @Inject
  LogFileCompressor(final SitePaths site) {
    logs_dir = resolve(site.logs_dir);
  }

  private static File resolve(final File logs_dir) {
    try {
      return logs_dir.getCanonicalFile();
    } catch (IOException e) {
      return logs_dir.getAbsoluteFile();
    }
  }

  @Override
  public void run() {
    final File[] list = logs_dir.listFiles();
    if (list == null) {
      return;
    }

    for (final File entry : list) {
      if (!isLive(entry) && !isCompressed(entry) && isLogFile(entry)) {
        compress(entry);
      }
    }
  }

  private boolean isLive(final File entry) {
    final String name = entry.getName();
    return ErrorLogFile.LOG_NAME.equals(name) //
        || "sshd_log".equals(name) //
        || "httpd_log".equals(name) //
        || "gerrit.run".equals(name) //
        || name.endsWith(".pid");
  }

  private boolean isCompressed(final File entry) {
    final String name = entry.getName();
    return name.endsWith(".gz") //
        || name.endsWith(".zip") //
        || name.endsWith(".bz2");
  }

  private boolean isLogFile(final File entry) {
    return entry.isFile();
  }

  private void compress(final File src) {
    final File dir = src.getParentFile();
    final File dst = new File(dir, src.getName() + ".gz");
    final File tmp = new File(dir, ".tmp." + src.getName());
    try {
      final InputStream in = new FileInputStream(src);
      try {
        OutputStream out = new GZIPOutputStream(new FileOutputStream(tmp));
        try {
          final byte[] buf = new byte[2048];
          int n;
          while (0 < (n = in.read(buf))) {
            out.write(buf, 0, n);
          }
        } finally {
          out.close();
        }
        tmp.setReadOnly();
      } finally {
        in.close();
      }
      if (!tmp.renameTo(dst)) {
        throw new IOException("Cannot rename " + tmp + " to " + dst);
      }
      src.delete();
    } catch (IOException e) {
      log.error("Cannot compress " + src, e);
      tmp.delete();
    }
  }

  @Override
  public String toString() {
    return "Log File Compressor";
  }
}
