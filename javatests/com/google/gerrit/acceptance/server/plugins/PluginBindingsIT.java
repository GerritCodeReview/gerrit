// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.plugins;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import org.junit.Test;

public class PluginBindingsIT extends AbstractDaemonTest {
  public static class TestPluginApiModule extends AbstractModule {}

  public static class TestPluginSysModule extends AbstractModule {}

  public static class PluginInjectsInjectorModule extends AbstractModule {
    private final Injector injector;

    @Inject
    PluginInjectsInjectorModule(Injector injector) {
      this.injector = injector;
    }

    @Override
    protected void configure() {
      Key<String> pluginNameKey = Key.get(String.class, PluginName.class);
      injector.getInstance(pluginNameKey);
    }
  }

  public static class PluginInjectsGerritReplicaModule extends AbstractModule {
    private final boolean isReplica;

    @Inject
    PluginInjectsGerritReplicaModule(@GerritIsReplica boolean isReplica) {
      this.isReplica = isReplica;
    }

    @Override
    protected void configure() {
      assertThat(isReplica).isFalse();
    }
  }

  @Test
  public void testCanInstallPluginInjectingInjector() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin-injecting-injector", PluginInjectsInjectorModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  public void testCanInstallPluginInjectingInjectorAfterInstallingApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin("my-plugin-injecting-injector", PluginInjectsInjectorModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  public void testCanInstallPluginInjectingReplica() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  public void testCanInstallPluginInjectingReplicaAfterInstallingApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin("my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }
}
