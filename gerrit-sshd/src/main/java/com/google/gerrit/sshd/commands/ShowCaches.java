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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheStats;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.h2.H2CacheImpl;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshDaemon;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
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
import java.util.SortedMap;

/** Show the current cache states. */
@RequiresCapability(GlobalCapability.VIEW_CACHES)
@CommandMetaData(name = "show-caches", descr = "Display current cache statistics")
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

    Map<String, H2CacheImpl<?, ?>> disks = Maps.newTreeMap();
    printMemoryCaches(disks, sortedCoreCaches());
    printMemoryCaches(disks, sortedPluginCaches());
    for (Map.Entry<String, H2CacheImpl<?, ?>> entry : disks.entrySet()) {
      H2CacheImpl<?, ?> cache = entry.getValue();
      CacheStats stat = cache.stats();
      H2CacheImpl.DiskStats disk = cache.diskStats();
      stdout.print(String.format(
          "D %-"+nw+"s|%6s %6s %7s| %7s |%4s %4s|\n",
          entry.getKey(),
          count(cache.size()),
          count(disk.size()),
          bytes(disk.space()),
          duration(stat.averageLoadPenalty()),
          percent(stat.hitCount(), stat.requestCount()),
          percent(disk.hitCount(), disk.requestCount())));
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

  private void printMemoryCaches(
      Map<String, H2CacheImpl<?, ?>> disks,
      Map<String, Cache<?,?>> caches) {
    for (Map.Entry<String, Cache<?,?>> entry : caches.entrySet()) {
      Cache<?,?> cache = entry.getValue();
      if (cache instanceof H2CacheImpl) {
        disks.put(entry.getKey(), (H2CacheImpl<?,?>)cache);
        continue;
      }
      CacheStats stat = cache.stats();
      stdout.print(String.format(
          "  %-"+nw+"s|%6s %6s %7s| %7s |%4s %4s|\n",
          entry.getKey(),
          count(cache.size()),
          "",
          "",
          duration(stat.averageLoadPenalty()),
          percent(stat.hitCount(), stat.requestCount()),
          ""));
    }
  }

  private Map<String, Cache<?, ?>> sortedCoreCaches() {
    SortedMap<String, Cache<?, ?>> m = Maps.newTreeMap();
    for (Map.Entry<String, Provider<Cache<?, ?>>> entry :
        cacheMap.byPlugin("gerrit").entrySet()) {
      m.put(cacheNameOf("gerrit", entry.getKey()), entry.getValue().get());
    }
    return m;
  }

  private Map<String, Cache<?, ?>> sortedPluginCaches() {
    SortedMap<String, Cache<?, ?>> m = Maps.newTreeMap();
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      if (!"gerrit".equals(e.getPluginName())) {
        m.put(cacheNameOf(e.getPluginName(), e.getExportName()),
            e.getProvider().get());
      }
    }
    return m;
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

  private String duration(double ns) {
    if (ns < 0.5) {
      return "";
    }
    String suffix = "ns";
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "us";
    }
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "ms";
    }
    if (ns >= 1000.0) {
      ns /= 1000.0;
      suffix = "s ";
    }
    return String.format("%4.1f%s", ns, suffix);
  }

  private String percent(final long value, final long total) {
    if (total <= 0) {
      return "";
    }
    final long pcent = (100 * value) / total;
    return String.format("%3d%%", (int) pcent);
  }
}
