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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;

@Singleton
public class GerritMetrics {
  private final MetricRegistry coreRegistry;
  private final Map<Object, String> pluginMetrics;
  private final Map<Object, MetricRegistry> pluginMetricRegistry;

  @Inject
  public GerritMetrics(MetricRegistry coreRegistry) {
    this.coreRegistry = coreRegistry;
    this.pluginMetrics = new HashMap<>();
    this.pluginMetricRegistry = new HashMap<>();
  }

  public MetricRegistry getNewPluginMetricRegistry(Object pluginOwner) {
    MetricRegistry registry = pluginMetricRegistry.get(pluginOwner);
    if (registry != null) {
      return registry;
    }
    registry =
        new MetricRegistryPluginProxy(pluginOwner, coreRegistry, pluginMetrics);
    pluginMetricRegistry.put(pluginOwner, registry);
    return registry;
  }

  public MetricRegistry getRegistry() {
    return coreRegistry;
  }
}
