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

package com.google.gerrit.acceptance.server.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.config.GerritIsReplicaProvider;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class GerritIsReplicaIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return new Config();
  }

  @Inject GerritIsReplicaProvider isReplicaProvider;

  @Test
  public void isNotReplica() {
    assertThat(isReplicaProvider.get()).isFalse();
    assertThat(server.isReplica()).isFalse();
  }

  @Test
  @UseLocalDisk
  public void isNotReplicaWithLocalDisk() {
    assertThat(isReplicaProvider.get()).isFalse();
    assertThat(server.isReplica()).isFalse();
  }

  @Test
  @Sandboxed
  public void isReplica() throws Exception {
    restartAsSlave();
    assertThat(isReplicaProvider.get()).isTrue();
    assertThat(server.isReplica()).isTrue();
  }

  @Test
  @GerritConfig(name = "container.replica", value = "true")
  public void isReplicaFromGerritConfigAnnotation() throws Exception {
    assertThat(isReplicaProvider.get()).isTrue();
    assertThat(server.isReplica()).isTrue();
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name = "container.replica", value = "true")
  public void isReplicaWithLocalDisk() throws Exception {
    assertThat(isReplicaProvider.get()).isTrue();
    assertThat(server.isReplica()).isTrue();
  }

  @Test
  @Sandboxed
  @UseLocalDisk
  public void isReplicaWithLocalDiskAfterRestart() throws Exception {
    restartAsSlave();
    assertThat(isReplicaProvider.get()).isTrue();
    assertThat(server.isReplica()).isTrue();
  }
}
