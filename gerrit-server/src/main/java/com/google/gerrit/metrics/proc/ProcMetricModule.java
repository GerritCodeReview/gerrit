// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.metrics.proc;

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Version;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("restriction")
public class ProcMetricModule extends MetricModule {
  @Override
  protected void configure(MetricMaker metrics) {
    buildLabel(metrics);
    procUptime(metrics);
    procCpuUsage(metrics);
    procJvmGc(metrics);
    procJvmMemory(metrics);
    procJvmThread(metrics);
  }

  private void buildLabel(MetricMaker metrics) {
    metrics.newConstantMetric(
        "build/label",
        Strings.nullToEmpty(Version.getVersion()),
        new Description("Version of Gerrit server software"));
  }

  private void procUptime(MetricMaker metrics) {
    metrics.newConstantMetric(
        "proc/birth_timestamp",
        Long.valueOf(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis())),
        new Description("Time at which the process started").setUnit(Units.MICROSECONDS));

    metrics.newCallbackMetric(
        "proc/uptime",
        Long.class,
        new Description("Uptime of this process").setUnit(Units.MILLISECONDS),
        new Supplier<Long>() {
          @Override
          public Long get() {
            return ManagementFactory.getRuntimeMXBean().getUptime();
          }
        });
  }

  private void procCpuUsage(MetricMaker metrics) {
    final OperatingSystemMXBean sys =
        (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    if (sys.getProcessCpuTime() != -1) {
      metrics.newCallbackMetric(
          "proc/cpu/usage",
          Double.class,
          new Description("CPU time used by the process").setCumulative().setUnit(Units.SECONDS),
          new Supplier<Double>() {
            @Override
            public Double get() {
              return sys.getProcessCpuTime() / 1e9;
            }
          });
    }
    if (sys instanceof UnixOperatingSystemMXBean) {
      final UnixOperatingSystemMXBean unix = (UnixOperatingSystemMXBean) sys;
      if (unix.getOpenFileDescriptorCount() != -1) {
        metrics.newCallbackMetric(
            "proc/num_open_fds",
            Long.class,
            new Description("Number of open file descriptors").setGauge().setUnit("fds"),
            new Supplier<Long>() {
              @Override
              public Long get() {
                return unix.getOpenFileDescriptorCount();
              }
            });
      }
    }
  }

  private void procJvmMemory(MetricMaker metrics) {
    final CallbackMetric0<Long> heapCommitted =
        metrics.newCallbackMetric(
            "proc/jvm/memory/heap_committed",
            Long.class,
            new Description("Amount of memory guaranteed for user objects.")
                .setGauge()
                .setUnit(Units.BYTES));

    final CallbackMetric0<Long> heapUsed =
        metrics.newCallbackMetric(
            "proc/jvm/memory/heap_used",
            Long.class,
            new Description("Amount of memory holding user objects.")
                .setGauge()
                .setUnit(Units.BYTES));

    final CallbackMetric0<Long> nonHeapCommitted =
        metrics.newCallbackMetric(
            "proc/jvm/memory/non_heap_committed",
            Long.class,
            new Description("Amount of memory guaranteed for classes, etc.")
                .setGauge()
                .setUnit(Units.BYTES));

    final CallbackMetric0<Long> nonHeapUsed =
        metrics.newCallbackMetric(
            "proc/jvm/memory/non_heap_used",
            Long.class,
            new Description("Amount of memory holding classes, etc.")
                .setGauge()
                .setUnit(Units.BYTES));

    final CallbackMetric0<Integer> objectPendingFinalizationCount =
        metrics.newCallbackMetric(
            "proc/jvm/memory/object_pending_finalization_count",
            Integer.class,
            new Description("Approximate number of objects needing finalization.")
                .setGauge()
                .setUnit("objects"));

    final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    metrics.newTrigger(
        ImmutableSet.<CallbackMetric<?>>of(
            heapCommitted, heapUsed, nonHeapCommitted, nonHeapUsed, objectPendingFinalizationCount),
        new Runnable() {
          @Override
          public void run() {
            try {
              MemoryUsage stats = memory.getHeapMemoryUsage();
              heapCommitted.set(stats.getCommitted());
              heapUsed.set(stats.getUsed());
            } catch (IllegalArgumentException e) {
              // MXBean may throw due to a bug in Java 7; ignore.
            }

            MemoryUsage stats = memory.getNonHeapMemoryUsage();
            nonHeapCommitted.set(stats.getCommitted());
            nonHeapUsed.set(stats.getUsed());

            objectPendingFinalizationCount.set(memory.getObjectPendingFinalizationCount());
          }
        });
  }

  private void procJvmGc(MetricMaker metrics) {
    final CallbackMetric1<String, Long> gcCount =
        metrics.newCallbackMetric(
            "proc/jvm/gc/count",
            Long.class,
            new Description("Number of GCs").setCumulative(),
            Field.ofString("gc_name", "The name of the garbage collector"));

    final CallbackMetric1<String, Long> gcTime =
        metrics.newCallbackMetric(
            "proc/jvm/gc/time",
            Long.class,
            new Description("Approximate accumulated GC elapsed time")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            Field.ofString("gc_name", "The name of the garbage collector"));

    metrics.newTrigger(
        gcCount,
        gcTime,
        new Runnable() {
          @Override
          public void run() {
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
              long count = gc.getCollectionCount();
              if (count != -1) {
                gcCount.set(gc.getName(), count);
              }
              long time = gc.getCollectionTime();
              if (time != -1) {
                gcTime.set(gc.getName(), time);
              }
            }
          }
        });
  }

  private void procJvmThread(MetricMaker metrics) {
    final ThreadMXBean thread = ManagementFactory.getThreadMXBean();
    metrics.newCallbackMetric(
        "proc/jvm/thread/num_live",
        Integer.class,
        new Description("Current live thread count").setGauge().setUnit("threads"),
        new Supplier<Integer>() {
          @Override
          public Integer get() {
            return thread.getThreadCount();
          }
        });
  }
}
