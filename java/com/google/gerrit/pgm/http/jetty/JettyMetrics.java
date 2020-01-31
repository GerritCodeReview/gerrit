// Copyright (C) 2020 The Android Open Source Project
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
package com.google.gerrit.pgm.http.jetty;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.metrics.CallbackMetric;
import com.google.gerrit.metrics.CallbackMetric0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class JettyMetrics {

  @Inject
  JettyMetrics(JettyServer jetty, MetricMaker metrics) {
    CallbackMetric0<Integer> minPoolSize =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/min_pool_size",
            Integer.class,
            new Description("Minimum thread pool size").setGauge());
    CallbackMetric0<Integer> maxPoolSize =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/max_pool_size",
            Integer.class,
            new Description("Maximum thread pool size").setGauge());
    CallbackMetric0<Integer> poolSize =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/pool_size",
            Integer.class,
            new Description("Current thread pool size").setGauge());
    CallbackMetric0<Integer> idleThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/idle_threads",
            Integer.class,
            new Description("Idle threads").setGauge().setUnit("threads"));
    CallbackMetric0<Integer> busyThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/active_threads",
            Integer.class,
            new Description("Active threads").setGauge().setUnit("threads"));
    CallbackMetric0<Integer> reservedThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/reserved_threads",
            Integer.class,
            new Description("Reserved threads").setGauge().setUnit("threads"));
    CallbackMetric0<Integer> queueSize =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/queue_size",
            Integer.class,
            new Description("Queued requests waiting for a thread").setGauge().setUnit("requests"));
    CallbackMetric0<Boolean> lowOnThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/is_low_on_threads",
            Boolean.class,
            new Description("Whether thread pool is low on threads").setGauge());
    JettyServer.Metrics jettyMetrics = jetty.getMetrics();
    metrics.newTrigger(
        ImmutableSet.<CallbackMetric<?>>of(
            idleThreads,
            busyThreads,
            reservedThreads,
            minPoolSize,
            maxPoolSize,
            poolSize,
            queueSize,
            lowOnThreads),
        () -> {
          minPoolSize.set(jettyMetrics.getMinThreads());
          maxPoolSize.set(jettyMetrics.getMaxThreads());
          poolSize.set(jettyMetrics.getThreads());
          idleThreads.set(jettyMetrics.getIdleThreads());
          busyThreads.set(jettyMetrics.getBusyThreads());
          reservedThreads.set(jettyMetrics.getReservedThreads());
          queueSize.set(jettyMetrics.getQueueSize());
          lowOnThreads.set(jettyMetrics.isLowOnThreads());
        });
  }
}
