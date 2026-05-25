// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.plugincontext.PluginContext;
import com.google.gerrit.server.plugincontext.PluginMapContext;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Guice;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class JettyServerThreadPoolTest {

  private WorkQueue workQueue;

  @Before
  public void setUp() {
    IdGenerator idGenerator = Guice.createInjector().getInstance(IdGenerator.class);
    workQueue =
        new WorkQueue(
            idGenerator,
            2,
            new DisabledMetricMaker(),
            new PluginMapContext<>(
                DynamicMap.emptyMap(), PluginContext.PluginMetrics.DISABLED_INSTANCE));
  }

  @Test
  public void defaultConfigUsesQueuedThreadPool() {
    Config cfg = new Config();

    ThreadPool pool = JettyServer.threadPool(cfg, new ThreadSettingsConfig(cfg), workQueue);

    assertThat(pool).isInstanceOf(QueuedThreadPool.class);
    assertThat(workQueue.getExecutor("HTTP")).isNull();
  }

  @Test
  public void useWorkQueueCreatesExecutorThreadPoolBackedByWorkQueue() {
    Config cfg = new Config();
    cfg.setBoolean("httpd", null, "useWorkQueue", true);
    cfg.setInt("httpd", null, "maxThreads", 25);

    ThreadPool pool = JettyServer.threadPool(cfg, new ThreadSettingsConfig(cfg), workQueue);

    assertThat(pool).isInstanceOf(ExecutorThreadPool.class);
    ScheduledThreadPoolExecutor httpExecutor = workQueue.getExecutor("HTTP");
    assertThat(httpExecutor).isNotNull();
    assertThat(httpExecutor.getCorePoolSize()).isEqualTo(25);
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  public void metricsReportQueueSizeInWorkQueueMode() {
    Config cfg = new Config();
    cfg.setBoolean("httpd", null, "useWorkQueue", true);
    cfg.setInt("httpd", null, "maxThreads", 1);

    ThreadPool pool = JettyServer.threadPool(cfg, new ThreadSettingsConfig(cfg), workQueue);
    JettyServer.Metrics metrics = new JettyServer.Metrics(pool, new ConnectionStatistics());

    ScheduledThreadPoolExecutor exec = workQueue.getExecutor("HTTP");
    exec.schedule(() -> {}, 10, TimeUnit.MINUTES);
    exec.schedule(() -> {}, 10, TimeUnit.MINUTES);

    assertThat(metrics.getQueueSize()).isEqualTo(2);
  }
}
