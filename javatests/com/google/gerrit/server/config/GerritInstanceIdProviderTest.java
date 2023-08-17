// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GerritInstanceIdProviderTest {
  private static final String ENV_VAR_VALUE = "id_1234";
  private static final List<String> INSTANCE_IDS_ENV_VARS =
      List.of("$INSTANCEID", "$INSTANCE_ID", "$instance_id", "$ID1234", "$instanceID_123");
  private static final List<String> INSTANCE_IDS_CONFIG =
      List.of("instance-1234", "$instance-id", "test 1234", "$test 1234", "$1234_abcd");

  @Mock EnvVarProvider envVarProvider;

  @Test
  public void providesInstanceIdFromValidEnvVar() {
    @GerritServerConfig Config cfg = new Config();

    for (String id : INSTANCE_IDS_ENV_VARS) {
      when(envVarProvider.get(id.substring(1))).thenReturn(ENV_VAR_VALUE);
      cfg.setString("gerrit", null, "instanceId", id);

      GerritInstanceIdProvider provider = new GerritInstanceIdProvider(cfg, envVarProvider);
      assertThat(provider.get()).isEqualTo(ENV_VAR_VALUE);
    }
  }

  @Test
  public void providesNullFromUnsetEnvVar() {
    @GerritServerConfig Config cfg = new Config();
    String id = "$UNSET";
    when(envVarProvider.get(id.substring(1))).thenReturn(null);
    cfg.setString("gerrit", null, "instanceId", id);

    GerritInstanceIdProvider provider = new GerritInstanceIdProvider(cfg, envVarProvider);
    assertThat(provider.get()).isEqualTo(null);
  }

  @Test
  public void providesInstanceIdFromConfig() {
    @GerritServerConfig Config cfg = new Config();

    for (String id : INSTANCE_IDS_CONFIG) {
      cfg.setString("gerrit", null, "instanceId", id);

      GerritInstanceIdProvider provider = new GerritInstanceIdProvider(cfg, envVarProvider);
      assertThat(provider.get()).isEqualTo(id);
    }
  }

  @Test
  public void providesNullFromUnsetConfig() {
    @GerritServerConfig Config cfg = new Config();
    GerritInstanceIdProvider provider = new GerritInstanceIdProvider(cfg, envVarProvider);
    assertThat(provider.get()).isEqualTo(null);
  }
}
