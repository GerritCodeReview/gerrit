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

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class PluginConfigFactory {
  private final Config cfg;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;

  @Inject
  PluginConfigFactory(@GerritServerConfig Config cfg,
      ProjectCache projectCache, ProjectState.Factory projectStateFactory) {
    this.cfg = cfg;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * 'gerrit.config' file.
   *
   * The returned plugin configuration provides access to all parameters of the
   * 'gerrit.config' file that are set in the 'plugin' subsection of the
   * specified plugin.
   *
   * E.g.:
   *   [plugin "my-plugin"]
   *     myKey = myValue
   *
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'gerrit.config' file
   */
  public PluginConfig getFromGerritConfig(String pluginName) {
    return new PluginConfig(pluginName, cfg);
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * 'project.config' file of the specified project.
   *
   * The returned plugin configuration provides access to all parameters of the
   * 'project.config' file that are set in the 'plugin' subsection of the
   * specified plugin.
   *
   * E.g.:
   *   [plugin "my-plugin"]
   *     myKey = myValue
   *
   * @param projectName the name of the project for which the plugin
   *        configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'project.config' file of the
   *         specified project
   * @throws NoSuchProjectException thrown if the specified project does not
   *         exist
   */
  public PluginConfig getFromProjectConfig(Project.NameKey projectName,
      String pluginName) throws NoSuchProjectException {
    ProjectState projectState = projectCache.get(projectName);
    if (projectState == null) {
      throw new NoSuchProjectException(projectName);
    }
    return projectState.getConfig().getPluginConfig(pluginName);
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * 'project.config' file of the specified project. Parameters which are not
   * set in the 'project.config' of this project are inherited from the parent
   * project's 'project.config' files.
   *
   * The returned plugin configuration provides access to all parameters of the
   * 'project.config' file that are set in the 'plugin' subsection of the
   * specified plugin.
   *
   * E.g.:
   * child project:
   *   [plugin "my-plugin"]
   *     myKey = childValue
   *
   * parent project:
   *   [plugin "my-plugin"]
   *     myKey = parentValue
   *     anotherKey = someValue
   *
   * return:
   *   [plugin "my-plugin"]
   *     myKey = childValue
   *     anotherKey = someValue
   *
   * @param projectName the name of the project for which the plugin
   *        configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'project.config' file of the
   *         specified project with inherited non-set parameters from the
   *         parent projects
   * @throws NoSuchProjectException thrown if the specified project does not
   *         exist
   */
  public PluginConfig getFromProjectConfigWithInheritance(
      Project.NameKey projectName, String pluginName)
      throws NoSuchProjectException {
    return getFromProjectConfig(projectName, pluginName).withInheritance(
        projectStateFactory);
  }
}
