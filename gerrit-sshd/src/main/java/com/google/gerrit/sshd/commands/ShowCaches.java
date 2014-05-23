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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Strings;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ListCaches;
import com.google.gerrit.server.config.ListCaches.CacheInfo;
import com.google.gerrit.server.config.ListCaches.CacheType;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.mina.MinaSession;
import org.apache.sshd.server.Environment;
import org.eclipse.jgit.internal.storage.file.WindowCacheStatAccessor;
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
import java.util.Map;

/** Show the current cache states. */
@RequiresCapability(GlobalCapability.VIEW_CACHES)
@CommandMetaData(name = "show-caches", description = "Display current cache statistics",
  runsAt = MASTER_OR_SLAVE)
final class ShowCaches extends SshCommand {
  private static volatile long serverStarted;

  static class StartupListener implements LifecycleListener {
    @Override
    public void start() {
      serverStarted = TimeUtil.nowMs();
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

  @Inject
  private Provider<ListCaches> listCaches;

  @Option(name = "--width", aliases = {"-w"}, metaVar = "COLS", usage = "width of output table")
  private int columns = 80;
  private int nw;

  @Override
  public void start(Environment env) throws IOException {
    String s = env.getEnv().get(Environment.ENV_COLUMNS);
    if (s != null && !s.isEmpty()) {
      try {
        columns = Integer.parseInt(s);
      } catch (NumberFormatException err) {
        columns = 80;
      }
    }
    super.start(env);
  }

  @Override
  protected void run() {
    nw = columns - 50;
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
        "%1s %-"+nw+"s|%-21s|  %-5s |%-9s|\n" //
        , "" //
        , "Name" //
        , "Entries" //
        , "AvgGet" //
        , "Hit Ratio" //
    ));
    stdout.print(String.format(//
        "%1s %-"+nw+"s|%6s %6s %7s|  %-5s  |%-4s %-4s|\n" //
        , "" //
        , "" //
        , "Mem" //
        , "Disk" //
        , "Space" //
        , "" //
        , "Mem" //
        , "Disk" //
    ));
    stdout.print("--");
    for (int i = 0; i < nw; i++) {
      stdout.print('-');
    }
    stdout.print("+---------------------+---------+---------+\n");

    Collection<CacheInfo> caches = getCaches();
    printMemoryCoreCaches(caches);
    printMemoryPluginCaches(caches);
    printDiskCaches(caches);
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

  private Collection<CacheInfo> getCaches() {
    Map<String, CacheInfo> caches = listCaches.get().apply(new ConfigResource());
    for (Map.Entry<String, CacheInfo> entry : caches.entrySet()) {
      CacheInfo cache = entry.getValue();
      if (cache.type == null) {
        cache.type = CacheType.MEM;
      }
      cache.name = entry.getKey();
    }
    return caches.values();
  }

  private void printMemoryCoreCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (!cache.name.contains("-") && CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printMemoryPluginCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (cache.name.contains("-") && CacheType.MEM.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printDiskCaches(Collection<CacheInfo> caches) {
    for (CacheInfo cache : caches) {
      if (CacheType.DISK.equals(cache.type)) {
        printCache(cache);
      }
    }
  }

  private void printCache(CacheInfo cache) {
    stdout.print(String.format(
        "%1s %-"+nw+"s|%6s %6s %7s| %7s |%4s %4s|\n",
        CacheType.DISK.equals(cache.type) ? "D" : "",
        cache.name,
        nullToEmpty(cache.entries.mem),
        nullToEmpty(cache.entries.disk),
        Strings.nullToEmpty(cache.entries.space),
        Strings.nullToEmpty(cache.averageGet),
        formatAsPercent(cache.hitRatio.mem),
        formatAsPercent(cache.hitRatio.disk)
      ));
  }

  private static String nullToEmpty(Long l) {
    return l != null ? String.valueOf(l) : "";
  }

  private static String formatAsPercent(Integer i) {
    return i != null ? String.valueOf(i) + "%" : "";
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
        case CANCELLED:
        case DONE:
        case OTHER:
          break;
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

    long now = TimeUtil.nowMs();
    Collection<IoSession> list = acceptor.getManagedSessions().values();
    long oldest = now;

    for (IoSession s : list) {
      if (s instanceof MinaSession) {
        MinaSession minaSession = (MinaSession)s;
        oldest = Math.min(oldest, minaSession.getSession().getCreationTime());
      }
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
    stdout.format("  on %s %s %s\n",
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
}
