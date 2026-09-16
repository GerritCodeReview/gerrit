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

package com.google.gerrit.server.project;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class LockKeyTest {
  @Test
  public void toStringJoinsNamespaceFunctionAndArgsWithTilde() {
    LockKey key = LockKey.of("gerrit", "create-project", "my-project");

    assertThat(key.toString()).isEqualTo("gerrit~create-project~my-project");
  }

  @Test
  public void toStringWithNoArgsJoinsNamespaceAndFunction() {
    LockKey key = LockKey.of("gerrit", "change-cleanup");

    assertThat(key.toString()).isEqualTo("gerrit~change-cleanup");
  }

  @Test
  public void toStringWithMultipleArgsJoinsAllOfThem() {
    LockKey key = LockKey.of("gerrit", "create-project", "my-project", "extra-scope");

    assertThat(key.toString()).isEqualTo("gerrit~create-project~my-project~extra-scope");
  }

  @Test
  public void coreUsesGerritNamespaceInStringForm() {
    LockKey key = LockKey.core("change-cleanup");

    assertThat(key.toString()).isEqualTo("gerrit~change-cleanup");
  }

  @Test
  public void pluginNamespacesUnderPluginsSlashPluginNameInStringForm() {
    LockKey key = LockKey.plugin("replication", "replicate-project", "my-project");

    assertThat(key.toString()).isEqualTo("plugins/replication~replicate-project~my-project");
  }

  @Test
  public void differentPluginsProduceDifferentStringsForTheSameFunctionality() {
    LockKey a = LockKey.plugin("plugin-a", "cleanup");
    LockKey b = LockKey.plugin("plugin-b", "cleanup");

    assertThat(a.toString()).isNotEqualTo(b.toString());
  }

  @Test
  public void equalKeysProduceTheSameString() {
    LockKey a = LockKey.core("create-project", "my-project");
    LockKey b = LockKey.core("create-project", "my-project");

    assertThat(a.toString()).isEqualTo(b.toString());
  }

  @Test
  public void argsAreDefensivelyCopiedBeforeStringFormatting() {
    String[] args = {"project-a"};
    LockKey key = LockKey.of("gerrit", "create-project", args);
    args[0] = "mutated";

    assertThat(key.toString()).isEqualTo("gerrit~create-project~project-a");
  }

  @Test
  public void nullNamespaceIsRejected() {
    assertThrows(NullPointerException.class, () -> LockKey.of(null, "change-cleanup"));
  }

  @Test
  public void nullFunctionIsRejected() {
    assertThrows(NullPointerException.class, () -> LockKey.of("gerrit", null));
  }
}
