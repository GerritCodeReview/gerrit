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

package com.google.gerrit.server.metrics;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.codahale.metrics.MetricRegistry;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;

@Singleton
public class GerritMetrics implements LifecycleListener {
  public final static MetricsReporter[] REPORTERS = {
      new GerritGraphiteReporter(),
      new GerritConsoleReporter()
  };

  public static final ArrayList<MetricsReporter> activeReporters =
      new ArrayList<>();

  static final MetricRegistry registry = new MetricRegistry();

  @Inject
  GerritMetrics(@GerritServerConfig Config config) {
    for (MetricsReporter reporter : REPORTERS) {
      if (config.getBoolean("metrics", reporter.getName(), "enabled", false)) {
        activeReporters.add(reporter.setup(config, registry));
      }
    }
  }

  @Override
  public void start() {
    for (MetricsReporter reporter : activeReporters) {
      reporter.start();
    }
  }

  @Override
  public void stop() {
    for (MetricsReporter reporter : activeReporters) {
      reporter.stop();
    }
  }

  public MetricRegistry getRegistry() {
    return registry;
  }
}
