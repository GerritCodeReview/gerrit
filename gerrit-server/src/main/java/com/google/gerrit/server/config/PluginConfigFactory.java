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

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Singleton
public class PluginConfigFactory {
  private static final Logger log =
      LoggerFactory.getLogger(PluginConfigFactory.class);

  private final SitePaths site;
  private final Config cfg;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final Map<String, Config> pluginConfigs;

  @Inject
  PluginConfigFactory(SitePaths site, @GerritServerConfig Config cfg,
      ProjectCache projectCache, ProjectState.Factory projectStateFactory) {
    this.site = site;
    this.cfg = cfg;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.pluginConfigs = Maps.newHashMap();
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
   * projects 'project.config' files.
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
   *         specified project with inheriting non-set parameters from the
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

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * plugin configuration file 'etc/<plugin-name>.config'.
   *
   * The plugin configuration is only loaded once and is then cached.
   *
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'etc/<plugin-name>.config' file
   */
  public Config getFromPluginConfig(String pluginName) {
    if (pluginConfigs.containsKey(pluginName)) {
      return pluginConfigs.get(pluginName);
    }

    File pluginConfigFile = new File(site.etc_dir, pluginName + ".config");
    FileBasedConfig cfg = new FileBasedConfig(pluginConfigFile, FS.DETECTED);
    pluginConfigs.put(pluginName, cfg);
    if (!cfg.getFile().exists()) {
      log.info("No " + pluginConfigFile.getAbsolutePath() + "; assuming defaults");
      return cfg;
    }

    try {
      cfg.load();
    } catch (IOException e) {
      log.warn("Failed to load " + pluginConfigFile.getAbsolutePath(), e);
    } catch (ConfigInvalidException e) {
      log.warn("Failed to load " + pluginConfigFile.getAbsolutePath(), e);
    }

    return cfg;
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * '<plugin-name>.config' file of the specified project.
   *
   * @param projectName the name of the project for which the plugin
   *        configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the '<plugin-name>.config' file of
   *         the specified project
   * @throws NoSuchProjectException thrown if the specified project does not
   *         exist
   */
  public Config getFromPluginConfig(Project.NameKey projectName,
      String pluginName) throws NoSuchProjectException {
    ProjectState projectState = projectCache.get(projectName);
    if (projectState == null) {
      throw new NoSuchProjectException(projectName);
    }
    return projectState.getConfig(pluginName + ".config").get();
  }
}
