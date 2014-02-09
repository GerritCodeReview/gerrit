// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

class DependencyResolver {

  static List<Plugin> topologicalSort(Collection<Plugin> plugins) {
    try {
      return TopologicalSort.sort(toDAGPlugins(plugins));
    } catch (DAGCycleException e) {
      throw new IllegalStateException("Cycle in directed graph");
    }
  }

  private static DirectedGraph<Plugin> toDAGPlugins(Collection<Plugin> plugins) {
    DirectedGraph<Plugin> g = new DirectedGraph<>();
    Map<String, Plugin> m =
        Maps.newHashMapWithExpectedSize(plugins.size());
    for (Plugin p : plugins) {
      g.addNode(p);
      m.put(p.getName(), p);
    }
    for (Plugin start : plugins) {
      for (String dependsOn : start.getDependencies()) {
        Plugin dest = m.get(dependsOn);
        if (dest == null) {
          PluginLoader.log.warn(String.format(
              "Missing dependency: plugin %s depends on %s that doesn't exist",
              start.getName(),
              dependsOn));
          continue;
        }
        g.addEdge(start, dest);
      }
    }
    return g;
  }

  static List<Plugin> reverseTopologicalSortSubgraph(Collection<Plugin> all,
      Set<Plugin> s) {
    try {
      List<Plugin> result = TopologicalSort.sortSubgraph(toDAGPlugins(all), s);
      Collections.reverse(result);
      return result;
    } catch (DAGCycleException e) {
      // Shouldn't happen, as DAG was already constructed
      throw new IllegalStateException(
          "Plugin dependency graph contains a cycle");
    }
  }

  // Topological ordering of a directed graph is a linear ordering
  // of its vertices such that for every directed edge uv from vertex
  // u to vertex v, u comes before v in the ordering.
  // In plugin domain however, plugin src depends on plugin dest
  // means that plugin dest must be loaded before plugin src.
  // We preserve the edge direction during the DAG construction
  // and reverse the resulting ordering to get the right sorting.
  static List<PluginDescriptor> reverseTopologicalSort(Map<String, File> p) {
    return reverseTopologicalSort(Lists.newArrayList(Iterables.transform(
        p.values(), new Function<File, PluginDescriptor>() {
          @Override
          public PluginDescriptor apply(File f) {
            return getDescriptor(f);
          }
        })));
  }

  private static List<PluginDescriptor> reverseTopologicalSort(
      List<PluginDescriptor> nodes) {
    DirectedGraph<PluginDescriptor> dag = toDAG(nodes);
    List<PluginDescriptor> sorted = sortEliminatingCycles(dag);
    Collections.reverse(sorted);
    return sorted;
  }

  private static List<PluginDescriptor> sortEliminatingCycles(
      DirectedGraph<PluginDescriptor> dag) {
    try {
      return TopologicalSort.sort(dag);
    } catch (DAGCycleException e) {
      PluginDescriptor dest = (PluginDescriptor)e.node;
      PluginLoader.log.warn(String.format(
          "Plugin dependency graph contains a cycle."
          + " Plugin %s is a part of the cycle."
          + " All dependencies to this plugin will be removed."),
          dest.name);
      for (PluginDescriptor start : dag.edgesTo(dest)) {
        PluginLoader.log.warn(String.format(
            "removing dependency from %s to %s",
            start.name,
            dest.name));
        dag.removeEdge(start, dest);
      }
      return sortEliminatingCycles(dag);
    }
  }

  private static DirectedGraph<PluginDescriptor> toDAG(
      List<PluginDescriptor> nodes) {
    DirectedGraph<PluginDescriptor> g = new DirectedGraph<>();
    Map<String, PluginDescriptor> m =
        Maps.newHashMapWithExpectedSize(nodes.size());
    for (PluginDescriptor p : nodes) {
      g.addNode(p);
      m.put(p.name, p);
    }
    for (PluginDescriptor src : nodes) {
      for (String dependsOn : src.deps) {
        PluginDescriptor dest = m.get(dependsOn);
        if (dest == null) {
          PluginLoader.log.warn(String.format(
              "Missing dependency: plugin %s depends on %s that doesn't exist",
              src.name,
              dependsOn));
          continue;
        }
        g.addEdge(src, dest);
      }
    }
    return g;
  }

  static PluginDescriptor getDescriptor(File f) {
    String n;
    String deps;
    try {
      n = Objects.firstNonNull(
          PluginLoader.getGerritPluginNameBootstrap(f),
          PluginLoader.nameOf(f));
      deps = Strings.nullToEmpty(getDependencies(f));
    } catch (IOException e) {
      PluginLoader.log.error(String.format(
          "Cannot read manifest for plugin %s: %s",
          f, e.getMessage()));
      n = PluginLoader.nameOf(f);
      deps = "";
    }
    return new PluginDescriptor(n, f,
        Sets.newLinkedHashSet(Splitter.on(',')
            .trimResults()
            .omitEmptyStrings()
            .split(deps)));
  }

  private static String getDependencies(File srcFile) throws IOException {
    if (PluginLoader.isJarPlugin(srcFile)) {
      JarFile jarFile = new JarFile(srcFile);
      try {
        return jarFile.getManifest().getMainAttributes()
            .getValue("Gerrit-Dependencies");
      } finally {
        jarFile.close();
      }
    }
    return null;
  }

  private DependencyResolver() {}
}
