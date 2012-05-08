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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.sshd.RequiresCapability;
import com.google.gerrit.sshd.SshDaemon;
import com.google.inject.Inject;

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Statistics;
import net.sf.ehcache.config.CacheConfiguration;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.eclipse.jgit.storage.file.WindowCacheStatAccessor;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

/** Show the current cache states. */
@RequiresCapability(GlobalCapability.VIEW_CACHES)
final class ShowCaches extends CacheCommand {
  private static volatile long serverStarted;

  static class StartupListener implements LifecycleListener {
    @Override
    public void start() {
      serverStarted = System.currentTimeMillis();
    }

    @Override
    public void stop() {
    }
  }

  @Option(name = "--gc", usage = "perform Java GC before printing memory stats")
  private boolean gc;

  @Option(name = "--show-jvm", usage = "show details about the JVM")
  private boolean showJVM;

  @Inject
  private WorkQueue workQueue;

  @Inject
  private SshDaemon daemon;

  @Inject
  @SitePath
  private File sitePath;

  @Override
  protected void run() {
    Date now = new Date();
    stdout.format(
        "%-25s %-20s      now  %16s\n",
        "Gerrit Code Review",
        Version.getVersion() != null ? Version.getVersion() : "",
        new SimpleDateFormat("HH:mm:ss   zzz").format(now));
    stdout.format(
        "%-25s %-20s   uptime %16s\n",
        "", "",
        uptime(now.getTime() - serverStarted));
    stdout.print('\n');

    stdout.print(String.format(//
        "%1s %-18s %-4s|%-20s|  %-5s  |%-14s|\n" //
        , "" //
        , "Name" //
        , "Max" //
        , "Object Count" //
        , "AvgGet" //
        , "Hit Ratio" //
    ));
    stdout.print(String.format(//
        "%1s %-18s %-4s|%6s %6s %6s|  %-5s   |%-4s %-4s %-4s|\n" //
        , "" //
        , "" //
        , "Age" //
        , "Disk" //
        , "Mem" //
        , "Cnt" //
        , "" //
        , "Disk" //
        , "Mem" //
        , "Agg" //
    ));
    stdout.print("------------------"
        + "-------+--------------------+----------+--------------+\n");
    for (final Ehcache cache : getAllCaches()) {
      final CacheConfiguration cfg = cache.getCacheConfiguration();
      final boolean useDisk = cfg.isDiskPersistent() || cfg.isOverflowToDisk();
      final Statistics stat = cache.getStatistics();
      final long total = stat.getCacheHits() + stat.getCacheMisses();

      if (useDisk) {
        stdout.print(String.format(//
            "D %-18s %-4s|%6s %6s %6s| %7s  |%4s %4s %4s|\n" //
            , cache.getName() //
            , interval(cfg.getTimeToLiveSeconds()) //
            , count(stat.getDiskStoreObjectCount()) //
            , count(stat.getMemoryStoreObjectCount()) //
            , count(stat.getObjectCount()) //
            , duration(stat.getAverageGetTime()) //
            , percent(stat.getOnDiskHits(), total) //
            , percent(stat.getInMemoryHits(), total) //
            , percent(stat.getCacheHits(), total) //
            ));
      } else {
        stdout.print(String.format(//
            "  %-18s %-4s|%6s %6s %6s| %7s  |%4s %4s %4s|\n" //
            , cache.getName() //
            , interval(cfg.getTimeToLiveSeconds()) //
            , "", "" //
            , count(stat.getObjectCount()) //
            , duration(stat.getAverageGetTime()) //
            , "", "" //
            , percent(stat.getCacheHits(), total) //
            ));
      }
    }
    stdout.print('\n');

    if (gc) {
      System.gc();
      System.runFinalization();
      System.gc();
    }

    sshSummary();
    taskSummary();
    memSummary();

    if (showJVM) {
      jvmSummary();
    }

    stdout.flush();
  }

  private void memSummary() {
    final Runtime r = Runtime.getRuntime();
    final long mMax = r.maxMemory();
    final long mFree = r.freeMemory();
    final long mTotal = r.totalMemory();
    final long mInuse = mTotal - mFree;

    final int jgitOpen = WindowCacheStatAccessor.getOpenFiles();
    final long jgitBytes = WindowCacheStatAccessor.getOpenBytes();

    stdout.format("Mem: %s total = %s used + %s free + %s buffers\n",
        bytes(mTotal),
        bytes(mInuse - jgitBytes),
        bytes(mFree),
        bytes(jgitBytes));
    stdout.format("     %s max\n", bytes(mMax));
    stdout.format("    %8d open files, %8d cpus available, %8d threads\n",
        jgitOpen,
        r.availableProcessors(),
        ManagementFactory.getThreadMXBean().getThreadCount());
    stdout.print('\n');
  }

  private void taskSummary() {
    Collection<Task<?>> pending = workQueue.getTasks();
    int tasksTotal = pending.size();
    int tasksRunning = 0, tasksReady = 0, tasksSleeping = 0;
    for (Task<?> task : pending) {
      switch (task.getState()) {
        case RUNNING: tasksRunning++; break;
        case READY: tasksReady++; break;
        case SLEEPING: tasksSleeping++; break;
      }
    }
    stdout.format(
        "Tasks: %4d  total = %4d running +   %4d ready + %4d sleeping\n",
        tasksTotal,
        tasksRunning,
        tasksReady,
        tasksSleeping);
  }

  private void sshSummary() {
    IoAcceptor acceptor = daemon.getIoAcceptor();
    if (acceptor == null) {
      return;
    }

    long now = System.currentTimeMillis();
    Collection<IoSession> list = acceptor.getManagedSessions().values();
    long oldest = now;
    for (IoSession s : list) {
      oldest = Math.min(oldest, s.getCreationTime());
    }

    stdout.format(
        "SSH:   %4d  users, oldest session started %s ago\n",
        list.size(),
        uptime(now - oldest));
  }

  private void jvmSummary() {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
    stdout.format("JVM: %s %s %s\n",
        runtimeBean.getVmVendor(),
        runtimeBean.getVmName(),
        runtimeBean.getVmVersion());
    stdout.format("  on %s %s %s\n", "",
        osBean.getName(),
        osBean.getVersion(),
        osBean.getArch());
    try {
      stdout.format("  running as %s on %s\n",
          System.getProperty("user.name"),
          InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
    }
    stdout.format("  cwd  %s\n", path(new File(".").getAbsoluteFile().getParentFile()));
    stdout.format("  site %s\n", path(sitePath));
  }

  private String path(File file) {
    try {
      return file.getCanonicalPath();
    } catch (IOException err) {
      return file.getAbsolutePath();
    }
  }

  private String uptime(long uptimeMillis) {
    if (uptimeMillis < 1000) {
      return String.format("%3d ms", uptimeMillis);
    }

    long uptime = uptimeMillis / 1000L;

    long min = uptime / 60;
    if (min < 60) {
      return String.format("%2d min %2d sec", min, uptime - min * 60);
    }

    long hr = uptime / 3600;
    if (hr < 24) {
      min = (uptime - hr * 3600) / 60;
      return String.format("%2d hrs %2d min", hr, min);
    }

    long days = uptime / (24 * 3600);
    hr = (uptime - (days * 24 * 3600)) / 3600;
    return String.format("%4d days %2d hrs", days, hr);
  }

  private String bytes(double value) {
    value /= 1024;
    String suffix = "k";

    if (value > 1024) {
      value /= 1024;
      suffix = "m";
    }
    if (value > 1024) {
      value /= 1024;
      suffix = "g";
    }
    return String.format("%1$6.2f%2$s", value, suffix);
  }

  private String count(long cnt) {
    if (cnt == 0) {
      return "";
    }
    return String.format("%6d", cnt);
  }

  private String duration(double ms) {
    if (Math.abs(ms) <= 0.05) {
      return "";
    }
    String suffix = "ms";
    if (ms >= 1000) {
      ms /= 1000;
      suffix = "s ";
    }
    return String.format("%4.1f%s", ms, suffix);
  }

  private String interval(double ttl) {
    if (ttl == 0) {
      return "inf";
    }

    String suffix = "s";
    if (ttl >= 60) {
      ttl /= 60;
      suffix = "m";

      if (ttl >= 60) {
        ttl /= 60;
        suffix = "h";
      }

      if (ttl >= 24) {
        ttl /= 24;
        suffix = "d";

        if (ttl >= 365) {
          ttl /= 365;
          suffix = "y";
        }
      }
    }

    return Integer.toString((int) ttl) + suffix;
  }

  private String percent(final long value, final long total) {
    if (total <= 0) {
      return "";
    }
    final long pcent = (100 * value) / total;
    return String.format("%3d%%", (int) pcent);
  }
}
