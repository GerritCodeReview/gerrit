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

package com.google.gerrit.server.plugins;

import com.google.gerrit.extensions.annotations.PluginCanonicalWebUrl;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.PluginUser;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ServerPluginInfoModule extends AbstractModule {
  private final ServerPlugin plugin;
  private final Path dataDir;

  private volatile boolean ready;
  private final MetricMaker serverMetrics;

  ServerPluginInfoModule(ServerPlugin plugin, MetricMaker serverMetrics) {
    this.plugin = plugin;
    this.dataDir = plugin.getDataDir();
    this.serverMetrics = serverMetrics;
  }

  @Override
  protected void configure() {
    bind(PluginUser.class).toInstance(plugin.getPluginUser());
    bind(String.class).annotatedWith(PluginName.class).toInstance(plugin.getName());
    bind(String.class)
        .annotatedWith(PluginCanonicalWebUrl.class)
        .toInstance(plugin.getPluginCanonicalWebUrl());

    install(
        new LifecycleModule() {
          @Override
          public void configure() {
            PluginMetricMaker metrics = new PluginMetricMaker(serverMetrics, plugin.getName());
            bind(MetricMaker.class).toInstance(metrics);
            listener().toInstance(metrics);
          }
        });
  }

  @Provides
  @PluginData
  Path getPluginData() {
    if (!ready) {
      synchronized (dataDir) {
        if (!ready) {
          try {
            Files.createDirectories(dataDir);
          } catch (IOException e) {
            throw new ProvisionException(
                String.format(
                    "Cannot create %s for plugin %s", dataDir.toAbsolutePath(), plugin.getName()),
                e);
          }
          ready = true;
        }
      }
    }
    return dataDir;
  }

  @Provides
  @PluginData
  File getPluginDataAsFile(@PluginData Path pluginData) {
    return pluginData.toFile();
  }
}
