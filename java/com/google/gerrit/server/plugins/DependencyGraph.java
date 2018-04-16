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

import com.google.common.collect.Sets;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** @ param <T> */
class DependencyGraph<T> {
  private final MutableGraph<T> graph = GraphBuilder.directed().allowsSelfLoops(false).build();

  /**  @return */
  public List<T> computeLoadOrder() {
    List<T> loadOrder = new ArrayList<>();

    MutableGraph<T> graphCopy = Graphs.copyOf(graph);
    Collection<T> nodes = graphCopy.nodes();
    Collection<T> toRemove = Sets.newHashSet();

    while (!nodes.isEmpty()) {
      for (T node : nodes) {
        if (!graphCopy.successors(node).isEmpty()) {
          continue;
        }
        loadOrder.add(node);
        toRemove.add(node);
      }

      if (toRemove.isEmpty()) {
        // The graph is not empty, but all the nodes have at least one unresolved dependency.
        // This means that the graph has a cycle.
        throw new CyclicDependencyException(graphCopy);
      }

      toRemove.forEach(graphCopy::removeNode);
      toRemove.clear();
    }

    return loadOrder;
  }

  /**  @param node */
  public void addNode(T node) {
    graph.addNode(node);
  }

  /**
   * @param parent
   * @param child
   */
  public void addDependency(T parent, T child) {
    graph.putEdge(parent, child);
  }

  static class CyclicDependencyException extends RuntimeException {
    private CyclicDependencyException(Graph graph) {
      super("Cyclic dependency found in graph " + graph.toString());
    }
  }
}
