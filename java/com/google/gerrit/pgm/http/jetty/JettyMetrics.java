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
import com.google.gerrit.metrics.Description.Units;
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
            new Description("Idle threads").setGauge());
    CallbackMetric0<Integer> busyThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/active_threads",
            Integer.class,
            new Description("Active threads").setGauge());
    CallbackMetric0<Integer> reservedThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/reserved_threads",
            Integer.class,
            new Description("Reserved threads").setGauge());
    CallbackMetric0<Integer> queueSize =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/queue_size",
            Integer.class,
            new Description("Queued requests waiting for a thread").setGauge());
    CallbackMetric0<Boolean> lowOnThreads =
        metrics.newCallbackMetric(
            "http/server/jetty/threadpool/is_low_on_threads",
            Boolean.class,
            new Description("Whether thread pool is low on threads").setGauge());
    CallbackMetric0<Long> connections =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/connections",
            Long.class,
            new Description("The current number of open connections").setGauge());
    CallbackMetric0<Long> connectionsTotal =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/connections_total",
            Long.class,
            new Description("The total number of connections opened").setGauge());
    CallbackMetric0<Long> connectionDurationMax =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/connections_duration_max",
            Long.class,
            new Description("The max duration of a connection")
                .setGauge()
                .setUnit(Units.MILLISECONDS));
    CallbackMetric0<Double> connectionDurationMean =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/connections_duration_mean",
            Double.class,
            new Description("The mean duration of a connection")
                .setGauge()
                .setUnit(Units.MILLISECONDS));
    CallbackMetric0<Double> connectionDurationStDev =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/connections_duration_stdev",
            Double.class,
            new Description("The standard deviation of the duration of a connection")
                .setGauge()
                .setUnit(Units.MILLISECONDS));
    CallbackMetric0<Long> receivedMessages =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/received_messages",
            Long.class,
            new Description("The total number of messages received").setGauge());
    CallbackMetric0<Long> sentMessages =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/sent_messages",
            Long.class,
            new Description("The total number of messages sent").setGauge());
    CallbackMetric0<Long> receivedBytes =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/received_bytes",
            Long.class,
            new Description("Total number of bytes received by tracked connections")
                .setGauge()
                .setUnit(Units.BYTES));
    CallbackMetric0<Long> sentBytes =
        metrics.newCallbackMetric(
            "http/server/jetty/connections/sent_bytes",
            Long.class,
            new Description("Total number of bytes sent by tracked connections")
                .setGauge()
                .setUnit(Units.BYTES));

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
            lowOnThreads,
            connections,
            connectionsTotal,
            connectionDurationMax,
            connectionDurationMean,
            connectionDurationStDev,
            receivedMessages,
            sentMessages,
            receivedBytes,
            sentBytes),
        () -> {
          minPoolSize.set(jettyMetrics.getMinThreads());
          maxPoolSize.set(jettyMetrics.getMaxThreads());
          poolSize.set(jettyMetrics.getThreads());
          idleThreads.set(jettyMetrics.getIdleThreads());
          busyThreads.set(jettyMetrics.getBusyThreads());
          reservedThreads.set(jettyMetrics.getReservedThreads());
          queueSize.set(jettyMetrics.getQueueSize());
          lowOnThreads.set(jettyMetrics.isLowOnThreads());
          connections.set(jettyMetrics.getConnections());
          connectionsTotal.set(jettyMetrics.getConnectionsTotal());
          connectionDurationMax.set(jettyMetrics.getConnectionDurationMax());
          connectionDurationMean.set(jettyMetrics.getConnectionDurationMean());
          connectionDurationStDev.set(jettyMetrics.getConnectionDurationStdDev());
          receivedMessages.set(jettyMetrics.getReceivedMessages());
          sentMessages.set(jettyMetrics.getSentMessages());
          receivedBytes.set(jettyMetrics.getReceivedBytes());
          sentBytes.set(jettyMetrics.getSentBytes());
        });
  }
}
