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
import com.google.inject.ProvisionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

class DependencyResolver {
  static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private DependencyResolver() {}

  // Topological ordering of a directed graph is a linear ordering
  // of its vertices such that for every directed edge uv from vertex
  // u to vertex v, u comes before v in the ordering.
  // In plugin domain however, plugin src depends on plugin dest
  // means that plugin dest must be loaded before plugin src.
  // We preserve the edge direction during the DAG construction
  // and reverse the resulting ordering to get the right sorting.
  static List<Dependency> sort(Map<String, File> activePlugins) {
    return reverseTopologicalSort(Lists.newArrayList(Iterables.transform(
        activePlugins.values(), new Function<File, Dependency>() {
          @Override
          public Dependency apply(File f) {
            String n;
            String deps;
            try {
              n =
                  Objects.firstNonNull(PluginLoader.getGerritPluginName(f),
                      PluginLoader.nameOf(f));
              deps = Strings.nullToEmpty(getDependencies(f));
            } catch (IOException e) {
              log.error(String.format("Cannot read manifest for plugin: %s", f));
              n = PluginLoader.nameOf(f);
              deps = "";
            }
            List<String> depList = Lists.newArrayList(Splitter.on(',')
                .trimResults().omitEmptyStrings().split(deps));
            if (depList.size() > 1) {
              throw new ProvisionException(
                  "Plugin cannot be loaded: "
                  + "Multiple plugin dependencies are currently not supported");
            }
            return new Dependency(n, f, depList);
          }
        })));
  }

  private static List<Dependency> reverseTopologicalSort(List<Dependency> nodes) {
    try {
      List<Dependency> sorted = TopologicalSort.sort(toDAG(nodes));
      Collections.reverse(sorted);
      return sorted;
    } catch (DAGCycleException e) {
      log.error("Plugin dependency graph contains a cycle", e);
      throw new ProvisionException("Plugin cannot be loaded: "
          + "dependency graph contains a cycle");
    }
  }

  private static DirectedGraph<Dependency> toDAG(List<Dependency> nodes) {
    DirectedGraph<Dependency> g = new DirectedGraph<>();
    Map<String, Dependency> m = Maps.newHashMapWithExpectedSize(nodes.size());
    for (Dependency p : nodes) {
      g.addNode(p);
      m.put(p.name, p);
    }
    for (Dependency src : nodes) {
      for (String dependsOn : src.deps) {
        Dependency dest = m.get(dependsOn);
        if (dest == null) {
          String msg = String.format(
              "Plugin %s depends on Plugin %s, that doesn't exist", src.name,
              dependsOn);
          log.error(msg);
          throw new ProvisionException(msg);
        }
        g.addEdge(src, dest);
      }
    }
    return g;
  }

  private static String getDependencies(File f) throws IOException {
    String fileName = f.getName();
    if (PluginLoader.isJarPlugin(fileName)) {
      JarFile jarFile = new JarFile(f);
      try {
        return jarFile.getManifest().getMainAttributes()
            .getValue("Gerrit-Dependencies");
      } finally {
        jarFile.close();
      }
    } else {
      // TODO(davido): externalize this mapping to some better place
      String extension = PluginLoader.getExtension(f);
      switch(extension) {
        case ".groovy":
        case ".gvy":
        case ".gy":
        case ".gsh":
          return "groovy-provider";
        default:
          return null;
      }
    }
  }
}
