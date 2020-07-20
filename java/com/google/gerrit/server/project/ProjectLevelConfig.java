// Copyright (C) 2013 The Android Open Source Project
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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

/** Configuration file in the projects refs/meta/config branch. */
public class ProjectLevelConfig {
  private final String fileName;
  private final ProjectState project;
  private Config cfg;

  public ProjectLevelConfig(String fileName, ProjectState project, Config cfg) {
    this.fileName = fileName;
    this.project = project;
    this.cfg = cfg;
  }

  public Config get() {
    if (cfg == null) {
      cfg = new Config();
    }
    return cfg;
  }

  public Config getWithInheritance() {
    return getWithInheritance(false);
  }

  /**
   * Get a Config that includes the values from all parent projects.
   *
   * <p>Merging means that matching sections/subsection will be merged to include the values from
   * both parent and child config.
   *
   * <p>No merging means that matching sections/subsections in the child project will replace the
   * corresponding value from the parent.
   *
   * @param merge whether to merge parent values with child values or not.
   * @return a combined config.
   */
  public Config getWithInheritance(boolean merge) {
    Config cfgWithInheritance = new Config();
    try {
      cfgWithInheritance.fromText(get().toText());
    } catch (ConfigInvalidException e) {
      // cannot happen
    }
    ProjectState parent = Iterables.getFirst(project.parents(), null);
    if (parent != null) {
      Config parentCfg = parent.getConfig(fileName).getWithInheritance();
      for (String section : parentCfg.getSections()) {
        Set<String> allNames = get().getNames(section);
        for (String name : parentCfg.getNames(section)) {
          String[] parentValues = parentCfg.getStringList(section, null, name);
          if (!allNames.contains(name)) {
            cfgWithInheritance.setStringList(section, null, name, Arrays.asList(parentValues));
          } else if (merge) {
            cfgWithInheritance.setStringList(
                section,
                null,
                name,
                Stream.concat(
                        Arrays.stream(cfg.getStringList(section, null, name)),
                        Arrays.stream(parentValues))
                    .sorted()
                    .distinct()
                    .collect(toList()));
          }
        }

        for (String subsection : parentCfg.getSubsections(section)) {
          allNames = get().getNames(section, subsection);
          for (String name : parentCfg.getNames(section, subsection)) {
            String[] parentValues = parentCfg.getStringList(section, subsection, name);
            if (!allNames.contains(name)) {
              cfgWithInheritance.setStringList(
                  section, subsection, name, Arrays.asList(parentValues));
            } else if (merge) {
              cfgWithInheritance.setStringList(
                  section,
                  subsection,
                  name,
                  Streams.concat(
                          Arrays.stream(cfg.getStringList(section, subsection, name)),
                          Arrays.stream(parentValues))
                      .sorted()
                      .distinct()
                      .collect(toList()));
            }
          }
        }
      }
    }
    return cfgWithInheritance;
  }
}
