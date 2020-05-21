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

package com.google.gerrit.acceptance.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Scopes;
import org.junit.Test;

@TestPlugin(
    name = "instance-id-from-plugin",
    sysModule = "com.google.gerrit.acceptance.config.InstanceIdFromPluginIT$Module")
public class InstanceIdFromPluginIT extends LightweightPluginDaemonTest {

  public static class Module extends AbstractModule {

    @Override
    protected void configure() {
      bind(InstanceIdLoader.class).in(Scopes.SINGLETON);
    }
  }

  public static class InstanceIdLoader {
    public final String gerritInstanceId;

    @Inject
    InstanceIdLoader(@Nullable @GerritInstanceId String gerritInstanceId) {
      this.gerritInstanceId = gerritInstanceId;
    }
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId")
  public void shouldReturnInstanceIdWhenDefined() {
    assertThat(getInstanceIdLoader().gerritInstanceId).isEqualTo("testInstanceId");
  }

  @Test
  public void shouldReturnNullWhenNotDefined() {
    assertThat(getInstanceIdLoader().gerritInstanceId).isNull();
  }

  private InstanceIdLoader getInstanceIdLoader() {
    return plugin.getSysInjector().getInstance(InstanceIdLoader.class);
  }
}
