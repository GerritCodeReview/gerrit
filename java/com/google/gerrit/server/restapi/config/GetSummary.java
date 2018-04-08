// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.config.SitePath;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Task;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.storage.file.WindowCacheStats;
import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.MAINTAIN_SERVER)
public class GetSummary implements RestReadView<ConfigResource> {

  private final WorkQueue workQueue;
  private final Path sitePath;

  @Option(name = "--gc", usage = "perform Java GC before retrieving memory stats")
  private boolean gc;

  public GetSummary setGc(boolean gc) {
    this.gc = gc;
    return this;
  }

  @Option(name = "--jvm", usage = "include details about the JVM")
  private boolean jvm;

  public GetSummary setJvm(boolean jvm) {
    this.jvm = jvm;
    return this;
  }

  @Inject
  public GetSummary(WorkQueue workQueue, @SitePath Path sitePath) {
    this.workQueue = workQueue;
    this.sitePath = sitePath;
  }

  @Override
  public SummaryInfo apply(ConfigResource rsrc) {
    if (gc) {
      System.gc();
      System.runFinalization();
      System.gc();
    }

    SummaryInfo summary = new SummaryInfo();
    summary.taskSummary = getTaskSummary();
    summary.memSummary = getMemSummary();
    summary.threadSummary = getThreadSummary();
    if (jvm) {
      summary.jvmSummary = getJvmSummary();
    }
    return summary;
  }

  private TaskSummaryInfo getTaskSummary() {
    Collection<Task<?>> pending = workQueue.getTasks();
    int tasksTotal = pending.size();
    int tasksRunning = 0;
    int tasksReady = 0;
    int tasksSleeping = 0;
    for (Task<?> task : pending) {
      switch (task.getState()) {
        case RUNNING:
          tasksRunning++;
          break;
        case READY:
          tasksReady++;
          break;
        case SLEEPING:
          tasksSleeping++;
          break;
        case CANCELLED:
        case DONE:
        case OTHER:
          break;
      }
    }

    TaskSummaryInfo taskSummary = new TaskSummaryInfo();
    taskSummary.total = toInteger(tasksTotal);
    taskSummary.running = toInteger(tasksRunning);
    taskSummary.ready = toInteger(tasksReady);
    taskSummary.sleeping = toInteger(tasksSleeping);
    return taskSummary;
  }

  private MemSummaryInfo getMemSummary() {
    Runtime r = Runtime.getRuntime();
    long mMax = r.maxMemory();
    long mFree = r.freeMemory();
    long mTotal = r.totalMemory();
    long mInuse = mTotal - mFree;

    int jgitOpen = WindowCacheStats.getOpenFiles();
    long jgitBytes = WindowCacheStats.getOpenBytes();

    MemSummaryInfo memSummaryInfo = new MemSummaryInfo();
    memSummaryInfo.total = bytes(mTotal);
    memSummaryInfo.used = bytes(mInuse - jgitBytes);
    memSummaryInfo.free = bytes(mFree);
    memSummaryInfo.buffers = bytes(jgitBytes);
    memSummaryInfo.max = bytes(mMax);
    memSummaryInfo.openFiles = toInteger(jgitOpen);
    return memSummaryInfo;
  }

  private ThreadSummaryInfo getThreadSummary() {
    Runtime r = Runtime.getRuntime();
    ThreadSummaryInfo threadInfo = new ThreadSummaryInfo();
    threadInfo.cpus = r.availableProcessors();
    threadInfo.threads = toInteger(ManagementFactory.getThreadMXBean().getThreadCount());

    List<String> prefixes =
        Arrays.asList(
            "H2",
            "HTTP",
            "IntraLineDiff",
            "ReceiveCommits",
            "SSH git-receive-pack",
            "SSH git-upload-pack",
            "SSH-Interactive-Worker",
            "SSH-Stream-Worker",
            "SshCommandStart",
            "sshd-SshServer");
    String other = "Other";
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

    threadInfo.counts = new HashMap<>();
    for (long id : threadMXBean.getAllThreadIds()) {
      ThreadInfo info = threadMXBean.getThreadInfo(id);
      if (info == null) {
        continue;
      }
      String name = info.getThreadName();
      Thread.State state = info.getThreadState();
      String group = other;
      for (String p : prefixes) {
        if (name.startsWith(p)) {
          group = p;
          break;
        }
      }
      Map<Thread.State, Integer> counts = threadInfo.counts.get(group);
      if (counts == null) {
        counts = new HashMap<>();
        threadInfo.counts.put(group, counts);
      }
      Integer c = counts.get(state);
      counts.put(state, c != null ? c + 1 : 1);
    }

    return threadInfo;
  }

  private JvmSummaryInfo getJvmSummary() {
    OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    JvmSummaryInfo jvmSummary = new JvmSummaryInfo();
    jvmSummary.vmVendor = runtimeBean.getVmVendor();
    jvmSummary.vmName = runtimeBean.getVmName();
    jvmSummary.vmVersion = runtimeBean.getVmVersion();
    jvmSummary.osName = osBean.getName();
    jvmSummary.osVersion = osBean.getVersion();
    jvmSummary.osArch = osBean.getArch();
    jvmSummary.user = System.getProperty("user.name");

    try {
      jvmSummary.host = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      // Ignored
    }

    jvmSummary.currentWorkingDirectory = path(Paths.get(".").toAbsolutePath().getParent());
    jvmSummary.site = path(sitePath);
    return jvmSummary;
  }

  private static Integer toInteger(int i) {
    return i != 0 ? i : null;
  }

  private static String bytes(double value) {
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
    return String.format("%1$6.2f%2$s", value, suffix).trim();
  }

  private static String path(Path path) {
    try {
      return path.toRealPath().normalize().toString();
    } catch (IOException err) {
      return path.toAbsolutePath().normalize().toString();
    }
  }

  public static class SummaryInfo {
    public TaskSummaryInfo taskSummary;
    public MemSummaryInfo memSummary;
    public ThreadSummaryInfo threadSummary;
    public JvmSummaryInfo jvmSummary;
  }

  public static class TaskSummaryInfo {
    public Integer total;
    public Integer running;
    public Integer ready;
    public Integer sleeping;
  }

  public static class MemSummaryInfo {
    public String total;
    public String used;
    public String free;
    public String buffers;
    public String max;
    public Integer openFiles;
  }

  public static class ThreadSummaryInfo {
    public Integer cpus;
    public Integer threads;
    public Map<String, Map<Thread.State, Integer>> counts;
  }

  public static class JvmSummaryInfo {
    public String vmVendor;
    public String vmName;
    public String vmVersion;
    public String osName;
    public String osVersion;
    public String osArch;
    public String user;
    public String host;
    public String currentWorkingDirectory;
    public String site;
  }
}
