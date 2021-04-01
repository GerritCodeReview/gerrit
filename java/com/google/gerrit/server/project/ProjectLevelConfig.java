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

import com.google.common.collect.Streams;
import com.google.gerrit.entities.ImmutableConfig;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;

/** Configuration file in the projects refs/meta/config branch. */
public class ProjectLevelConfig {
  /**
   * This class is a low-level API that allows callers to read the config directly from a repository
   * and make updates to it.
   */
  public static class Bare extends VersionedMetaData {
    private final String fileName;
    @Nullable private Config cfg;

    public Bare(String fileName) {
      this.fileName = fileName;
      this.cfg = null;
    }

    public Config getConfig() {
      if (cfg == null) {
        cfg = new Config();
      }
      return cfg;
    }

    @Override
    protected String getRefName() {
      return RefNames.REFS_CONFIG;
    }

    @Override
    protected void onLoad() throws IOException, ConfigInvalidException {
      cfg = readConfig(fileName);
    }

    @Override
    protected boolean onSave(CommitBuilder commit) throws IOException {
      if (commit.getMessage() == null || "".equals(commit.getMessage())) {
        commit.setMessage("Updated configuration\n");
      }
      saveConfig(fileName, cfg);
      return true;
    }
  }

  private final String fileName;
  private final ProjectState project;
  private final ImmutableConfig immutableConfig;

  public ProjectLevelConfig(
      String fileName, ProjectState project, @Nullable ImmutableConfig immutableConfig) {
    this.fileName = fileName;
    this.project = project;
    this.immutableConfig = immutableConfig == null ? ImmutableConfig.EMPTY : immutableConfig;
  }

  public Config get() {
    return immutableConfig.mutableCopy();
  }

  public Config getWithInheritance() {
    return getWithInheritance(/* merge= */ false);
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
    Config cfg = new Config();
    // Traverse from All-Projects down to the current project
    StreamSupport.stream(project.treeInOrder().spliterator(), false)
        .forEach(
            parent -> {
              ImmutableConfig levelCfg = parent.getConfig(fileName).immutableConfig;
              for (String section : levelCfg.getSections()) {
                Set<String> allNames = cfg.getNames(section);
                for (String name : levelCfg.getNames(section)) {
                  String[] levelValues = levelCfg.getStringList(section, null, name);
                  if (allNames.contains(name) && merge) {
                    cfg.setStringList(
                        section,
                        null,
                        name,
                        Stream.concat(
                                Arrays.stream(cfg.getStringList(section, null, name)),
                                Arrays.stream(levelValues))
                            .sorted()
                            .distinct()
                            .collect(toList()));
                  } else {
                    cfg.setStringList(section, null, name, Arrays.asList(levelValues));
                  }
                }

                for (String subsection : levelCfg.getSubsections(section)) {
                  allNames = cfg.getNames(section, subsection);

                  Set<String> allNamesLevelCfg = levelCfg.getNames(section, subsection);
                  if (allNamesLevelCfg.isEmpty()) {
                    // Set empty subsection.
                    cfg.setString(section, subsection, null, null);
                  } else {
                    for (String name : allNamesLevelCfg) {
                      String[] levelValues = levelCfg.getStringList(section, subsection, name);
                      if (allNames.contains(name) && merge) {
                        cfg.setStringList(
                            section,
                            subsection,
                            name,
                            Streams.concat(
                                    Arrays.stream(cfg.getStringList(section, subsection, name)),
                                    Arrays.stream(levelValues))
                                .sorted()
                                .distinct()
                                .collect(toList()));
                      } else {
                        cfg.setStringList(section, subsection, name, Arrays.asList(levelValues));
                      }
                    }
                  }
                }
              }
            });
    return cfg;
  }
}
