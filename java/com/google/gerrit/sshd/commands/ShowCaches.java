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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Strings;
import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.config.GetSummary;
import com.google.gerrit.server.restapi.config.GetSummary.JvmSummaryInfo;
import com.google.gerrit.server.restapi.config.GetSummary.MemSummaryInfo;
import com.google.gerrit.server.restapi.config.GetSummary.SummaryInfo;
import com.google.gerrit.server.restapi.config.GetSummary.TaskSummaryInfo;
import com.google.gerrit.server.restapi.config.GetSummary.ThreadSummaryInfo;
import com.google.gerrit.server.restapi.config.ListCaches;
import com.google.gerrit.server.restapi.config.ListCaches.CacheInfo;
import com.google.gerrit.server.restapi.config.ListCaches.CacheType;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gerrit.sshd.SshDaemon;
import com.google.inject.Inject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.mina.MinaSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.kohsuke.args4j.Option;

/** Show the current cache states. */
@RequiresAnyCapability({VIEW_CACHES, MAINTAIN_SERVER})
@CommandMetaData(
    name = "show-caches",
    description = "Display current cache statistics",
    runsAt = MASTER_OR_SLAVE)
final class ShowCaches extends SshCommand {
  private static volatile long serverStarted;

  static class StartupListener implements LifecycleListener {
    @Override
    public void start() {
      serverStarted = TimeUtil.nowMs();
    }

    @Override
    public void stop() {}
  }

  @Option(name = "--gc", usage = "perform Java GC before printing memory stats")
  private boolean gc;

  @Option(name = "--show-jvm", usage = "show details about the JVM")
  private boolean showJVM;

  @Option(name = "--show-threads", usage = "show detailed thread counts")
  private boolean showThreads;

  @Inject private SshDaemon daemon;
  @Inject private ListCaches listCaches;
  @Inject private GetSummary getSummary;
  @Inject private CurrentUser self;
  @Inject private PermissionBackend permissionBackend;

  @Option(
      name = "--width",
      aliases = {"-w"},
      metaVar = "COLS",
      usage = "width of output table")
  private int columns = 80;

  private int nw;

  @Override
  public void start(ChannelSession channel, Environment env) throws IOException {
    String s = env.getEnv().get(Environment.ENV_COLUMNS);
    if (s != null && !s.isEmpty()) {
      try {
        columns = Integer.parseInt(s);
      } catch (NumberFormatException err) {
        columns = 80;
      }
    }
    super.start(channel, env);
  }

  @Override
  protected void run() throws Failure {
    nw = columns - 50;
    Date now = new Date();
    stdout.format(
        "%-25s %-20s      now  %16s\n",
        "Gerrit Code Review",
        Version.getVersion() != null ? Version.getVersion() : "",
        new SimpleDateFormat("HH:mm:ss   zzz").format(now));
    stdout.format("%-25s %-20s   uptime %16s\n", "", "", uptime(now.getTime() - serverStarted));
    stdout.print('\n');

    stdout.print(
        String.format( //
            "%1s %-" + nw + "s|%-21s|  %-5s |%-9s|\n" //
            ,
            "" //
            ,
            "Name" //
            ,
            "Entries" //
            ,
            "AvgGet" //
            ,
            "Hit Ratio" //
            ));
    stdout.print(
        String.format( //
            "%1s %-" + nw + "s|%6s %6s %7s|  %-5s  |%-4s %-4s|\n" //
            ,
            "" //
            ,
            "" //
            ,
            "Mem" //
            ,
            "Disk" //
            ,
            "Space" //
            ,
            "" //
            ,
            "Mem" //
            ,
            "Disk" //
            ));
    stdout.print("--");
    for (int i = 0; i < nw; i++) {
      stdout.print('-');
    }
    stdout.print("+---------------------+---------+---------+\n");

    try {
      Collection<CacheInfo> caches = getCaches();
      printMemoryCoreCaches(caches);
      printMemoryPluginCaches(caches);
      printDiskCaches(caches);
      stdout.print('\n');

      boolean showJvm;
      try {
        permissionBackend.user(self).check(GlobalPermission.MAINTAIN_SERVER);
        showJvm = true;
      } catch (AuthException | PermissionBackendException e) {
        // Silently ignore and do not display detailed JVM information.
        showJvm = false;
      }
      if (showJvm) {
        sshSummary();

        SummaryInfo summary =
            getSummary.setGc(gc).setJvm(showJVM).apply(new ConfigResource()).value();
        taskSummary(summary.taskSummary);
        memSummary(summary.memSummary);
        threadSummary(summary.threadSummary);

        if (showJVM && summary.jvmSummary != null) {
          jvmSummary(summary.jvmSummary);
        }
      }
    } catch (Exception e) {
      throw new Failure(1, "unavailable", e);
    }

    stdout.flush();
  }

  private Collection<CacheInfo> getCaches() {
    @SuppressWarnings("unchecked")
    Map<String, CacheInfo> caches =
        (Map<String, CacheInfo>) listCaches.apply(new ConfigResource()).value();
    for (Map.Entry<String, CacheInfo> entry : caches.entrySet()) {
      CacheInfo cache = entry.getValue();
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
    stdout.print(
        String.format(
            "%1s %-" + nw + "s|%6s %6s %7s| %7s |%4s %4s|\n",
            CacheType.DISK.equals(cache.type) ? "D" : "",
            cache.name,
            nullToEmpty(cache.entries.mem),
            nullToEmpty(cache.entries.disk),
            Strings.nullToEmpty(cache.entries.space),
            Strings.nullToEmpty(cache.averageGet),
            formatAsPercent(cache.hitRatio.mem),
            formatAsPercent(cache.hitRatio.disk)));
  }

  private static String nullToEmpty(Long l) {
    return l != null ? String.valueOf(l) : "";
  }

  private static String formatAsPercent(Integer i) {
    return i != null ? String.valueOf(i) + "%" : "";
  }

  private void memSummary(MemSummaryInfo memSummary) {
    stdout.format(
        "Mem: %s total = %s used + %s free + %s buffers\n",
        memSummary.total, memSummary.used, memSummary.free, memSummary.buffers);
    stdout.format("     %s max\n", memSummary.max);
    stdout.format("    %8d open files\n", nullToZero(memSummary.openFiles));
    stdout.print('\n');
  }

  private void threadSummary(ThreadSummaryInfo threadSummary) {
    stdout.format(
        "Threads: %d CPUs available, %d threads\n", threadSummary.cpus, threadSummary.threads);

    if (showThreads) {
      stdout.print(String.format("  %22s", ""));
      for (Thread.State s : Thread.State.values()) {
        stdout.print(String.format(" %14s", s.name()));
      }
      stdout.print('\n');
      for (Map.Entry<String, Map<Thread.State, Integer>> e : threadSummary.counts.entrySet()) {
        stdout.print(String.format("  %-22s", e.getKey()));
        for (Thread.State s : Thread.State.values()) {
          stdout.print(String.format(" %14d", nullToZero(e.getValue().get(s))));
        }
        stdout.print('\n');
      }
    }
    stdout.print('\n');
  }

  private void taskSummary(TaskSummaryInfo taskSummary) {
    stdout.format(
        "Tasks: %4d  total = %4d running +   %4d ready + %4d sleeping\n",
        nullToZero(taskSummary.total),
        nullToZero(taskSummary.running),
        nullToZero(taskSummary.ready),
        nullToZero(taskSummary.sleeping));
  }

  private static int nullToZero(Integer i) {
    return i != null ? i : 0;
  }

  private static long nullToZero(Long i) {
    return i != null ? i : 0;
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
        MinaSession minaSession = (MinaSession) s;
        oldest = Math.min(oldest, minaSession.getSession().getCreationTime());
      }
    }

    stdout.format(
        "SSH:   %4d  users, oldest session started %s ago\n", list.size(), uptime(now - oldest));
  }

  private void jvmSummary(JvmSummaryInfo jvmSummary) {
    stdout.format("JVM: %s %s %s\n", jvmSummary.vmVendor, jvmSummary.vmName, jvmSummary.vmVersion);
    stdout.format("  on %s %s %s\n", jvmSummary.osName, jvmSummary.osVersion, jvmSummary.osArch);
    stdout.format("  running as %s on %s\n", jvmSummary.user, Strings.nullToEmpty(jvmSummary.host));
    stdout.format("  cwd  %s\n", jvmSummary.currentWorkingDirectory);
    stdout.format("  site %s\n", jvmSummary.site);
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
}
