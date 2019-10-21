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
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Version;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

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
        ManagementFactory.getRuntimeMXBean()::getUptime);
  }

  private void procCpuUsage(MetricMaker metrics) {
    OperatingSystemMXBeanInterface provider = OperatingSystemMXBeanFactory.create();

    if (provider == null) {
      return;
    }

    if (provider.getProcessCpuTime() != -1) {
      metrics.newCallbackMetric(
          "proc/cpu/usage",
          Double.class,
          new Description("CPU time used by the process").setCumulative().setUnit(Units.SECONDS),
          () -> provider.getProcessCpuTime() / 1e9);
    }

    if (provider.getOpenFileDescriptorCount() != -1) {
      metrics.newCallbackMetric(
          "proc/num_open_fds",
          Long.class,
          new Description("Number of open file descriptors").setGauge().setUnit("fds"),
          provider::getOpenFileDescriptorCount);
    }
  }

  private void procJvmMemory(MetricMaker metrics) {
    CallbackMetric0<Long> heapCommitted =
        metrics.newCallbackMetric(
            "proc/jvm/memory/heap_committed",
            Long.class,
            new Description("Amount of memory guaranteed for user objects.")
                .setGauge()
                .setUnit(Units.BYTES));

    CallbackMetric0<Long> heapUsed =
        metrics.newCallbackMetric(
            "proc/jvm/memory/heap_used",
            Long.class,
            new Description("Amount of memory holding user objects.")
                .setGauge()
                .setUnit(Units.BYTES));

    CallbackMetric0<Long> nonHeapCommitted =
        metrics.newCallbackMetric(
            "proc/jvm/memory/non_heap_committed",
            Long.class,
            new Description("Amount of memory guaranteed for classes, etc.")
                .setGauge()
                .setUnit(Units.BYTES));

    CallbackMetric0<Long> nonHeapUsed =
        metrics.newCallbackMetric(
            "proc/jvm/memory/non_heap_used",
            Long.class,
            new Description("Amount of memory holding classes, etc.")
                .setGauge()
                .setUnit(Units.BYTES));

    CallbackMetric0<Integer> objectPendingFinalizationCount =
        metrics.newCallbackMetric(
            "proc/jvm/memory/object_pending_finalization_count",
            Integer.class,
            new Description("Approximate number of objects needing finalization.")
                .setGauge()
                .setUnit("objects"));

    MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
    metrics.newTrigger(
        ImmutableSet.of(
            heapCommitted, heapUsed, nonHeapCommitted, nonHeapUsed, objectPendingFinalizationCount),
        () -> {
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
        });
  }

  private void procJvmGc(MetricMaker metrics) {
    Field<String> gcNameField =
        Field.ofString("gc_name", Metadata.Builder::garbageCollectorName)
            .description("The name of the garbage collector")
            .build();

    CallbackMetric1<String, Long> gcCount =
        metrics.newCallbackMetric(
            "proc/jvm/gc/count",
            Long.class,
            new Description("Number of GCs").setCumulative(),
            gcNameField);

    CallbackMetric1<String, Long> gcTime =
        metrics.newCallbackMetric(
            "proc/jvm/gc/time",
            Long.class,
            new Description("Approximate accumulated GC elapsed time")
                .setCumulative()
                .setUnit(Units.MILLISECONDS),
            gcNameField);

    metrics.newTrigger(
        gcCount,
        gcTime,
        () -> {
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
        });
  }

  private void procJvmThread(MetricMaker metrics) {
    ThreadMXBean thread = ManagementFactory.getThreadMXBean();
    metrics.newCallbackMetric(
        "proc/jvm/thread/num_live",
        Integer.class,
        new Description("Current live thread count").setGauge().setUnit("threads"),
        thread::getThreadCount);
  }
}
