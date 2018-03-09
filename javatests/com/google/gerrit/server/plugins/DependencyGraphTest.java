// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DependencyGraphTest {
  private static final String PLUGIN_A = "Plugin A";
  private static final String PLUGIN_B = "Plugin B";
  private static final String PLUGIN_C = "Plugin C";
  private static final String PLUGIN_D = "Plugin D";
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void returnsACorrectLoadOrder() {

    DependencyGraph<String> dependencyGraph = new DependencyGraph<>();
    dependencyGraph.addNode(PLUGIN_A);
    dependencyGraph.addNode(PLUGIN_B);
    dependencyGraph.addNode(PLUGIN_C);
    dependencyGraph.addNode(PLUGIN_D);

    // A -> C, D -> B -> C
    dependencyGraph.addDependency(PLUGIN_A, PLUGIN_C);

    dependencyGraph.addDependency(PLUGIN_D, PLUGIN_B);
    dependencyGraph.addDependency(PLUGIN_B, PLUGIN_C);

    List<String> loadOrder = dependencyGraph.computeLoadOrder();

    // We have to load C first, then (A, B) and finally D
    int orderA = loadOrder.indexOf(PLUGIN_A);
    int orderB = loadOrder.indexOf(PLUGIN_B);
    int orderC = loadOrder.indexOf(PLUGIN_C);
    int orderD = loadOrder.indexOf(PLUGIN_D);

    assertThat(orderA).isGreaterThan(orderC);
    assertThat(orderB).isGreaterThan(orderC);
    assertThat(orderD).isGreaterThan(orderC);

    assertThat(orderD).isGreaterThan(orderB);
  }

  @Test
  public void throwsOnCyclicDependency() {
    DependencyGraph<String> dependencyGraph = new DependencyGraph<>();
    dependencyGraph.addNode(PLUGIN_A);
    dependencyGraph.addNode(PLUGIN_B);
    dependencyGraph.addNode(PLUGIN_C);

    // A -> B -> C -> A ...
    dependencyGraph.addDependency(PLUGIN_A, PLUGIN_B);
    dependencyGraph.addDependency(PLUGIN_B, PLUGIN_C);
    dependencyGraph.addDependency(PLUGIN_C, PLUGIN_A);

    exception.expect(DependencyGraph.CyclicDependencyException.class);
    dependencyGraph.computeLoadOrder();
  }
}
