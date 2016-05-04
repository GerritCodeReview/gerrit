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
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class PluginConfigFactory implements ReloadPluginListener {
  private static final Logger log =
      LoggerFactory.getLogger(PluginConfigFactory.class);
  private static final String EXTENSION = ".config";

  private final SitePaths site;
  private final GerritServerConfigProvider cfgProvider;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final Map<String, Config> pluginConfigs;

  private volatile FileSnapshot cfgSnapshot;
  private volatile Config cfg;

  @Inject
  PluginConfigFactory(SitePaths site, GerritServerConfigProvider cfgProvider,
      ProjectCache projectCache, ProjectState.Factory projectStateFactory) {
    this.site = site;
    this.cfgProvider = cfgProvider;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.pluginConfigs = new HashMap<>();

    this.cfgSnapshot = FileSnapshot.save(site.gerrit_config.toFile());
    this.cfg = cfgProvider.get();
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
    return getFromGerritConfig(pluginName, false);
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * 'gerrit.config' file.
   *
   * The returned plugin configuration provides access to all parameters of the
   * 'gerrit.config' file that are set in the 'plugin' subsection of the
   * specified plugin.
   *
   * E.g.: [plugin "my-plugin"] myKey = myValue
   *
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @param refresh if <code>true</code> it is checked if the 'gerrit.config'
   *        file was modified and if yes the Gerrit configuration is reloaded,
   *        if <code>false</code> the cached Gerrit configuration is used
   * @return the plugin configuration from the 'gerrit.config' file
   */
  public PluginConfig getFromGerritConfig(String pluginName, boolean refresh) {
    File configFile = site.gerrit_config.toFile();
    if (refresh && cfgSnapshot.isModified(configFile)) {
      cfgSnapshot = FileSnapshot.save(configFile);
      cfg = cfgProvider.get();
    }
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
    return getFromProjectConfig(projectState, pluginName);
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * 'project.config' file of the specified project.
   *
   * The returned plugin configuration provides access to all parameters of the
   * 'project.config' file that are set in the 'plugin' subsection of the
   * specified plugin.
   *
   * E.g.: [plugin "my-plugin"] myKey = myValue
   *
   * @param projectState the project for which the plugin configuration should
   *        be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'project.config' file of the
   *         specified project
   */
  public PluginConfig getFromProjectConfig(ProjectState projectState,
      String pluginName) {
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
   * E.g.: child project: [plugin "my-plugin"] myKey = childValue
   *
   * parent project: [plugin "my-plugin"] myKey = parentValue anotherKey =
   * someValue
   *
   * return: [plugin "my-plugin"] myKey = childValue anotherKey = someValue
   *
   * @param projectState the project for which the plugin configuration should
   *        be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the 'project.config' file of the
   *         specified project with inherited non-set parameters from the parent
   *         projects
   */
  public PluginConfig getFromProjectConfigWithInheritance(
      ProjectState projectState, String pluginName) {
    return getFromProjectConfig(projectState, pluginName).withInheritance(
        projectStateFactory);
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * plugin configuration file '{@code etc/<plugin-name>.config}'.
   *
   * The plugin configuration is only loaded once and is then cached.
   *
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the
   *         '{@code etc/<plugin-name>.config}' file
   */
  public synchronized Config getGlobalPluginConfig(String pluginName) {
    if (pluginConfigs.containsKey(pluginName)) {
      return pluginConfigs.get(pluginName);
    }

    Path pluginConfigFile = site.etc_dir.resolve(pluginName + ".config");
    FileBasedConfig cfg =
        new FileBasedConfig(pluginConfigFile.toFile(), FS.DETECTED);
    pluginConfigs.put(pluginName, cfg);
    if (!cfg.getFile().exists()) {
      log.info("No " + pluginConfigFile.toAbsolutePath() + "; assuming defaults");
      return cfg;
    }

    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      log.warn("Failed to load " + pluginConfigFile.toAbsolutePath(), e);
    }

    return cfg;
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * '{@code <plugin-name>.config}' file in the 'refs/meta/config' branch of
   * the specified project.
   *
   * @param projectName the name of the project for which the plugin
   *        configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the '{@code <plugin-name>.config}'
   *         file of the specified project
   * @throws NoSuchProjectException thrown if the specified project does not
   *         exist
   */
  public Config getProjectPluginConfig(Project.NameKey projectName,
      String pluginName) throws NoSuchProjectException {
    return getPluginConfig(projectName, pluginName).get();
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * '{@code <plugin-name>.config}' file in the 'refs/meta/config' branch of
   * the specified project.
   *
   * @param projectState the project for which the plugin configuration should
   *        be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the '{@code <plugin-name>.config}'
   *         file of the specified project
   */
  public Config getProjectPluginConfig(ProjectState projectState,
      String pluginName) {
    return projectState.getConfig(pluginName + EXTENSION).get();
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * '{@code <plugin-name>.config}' file in the 'refs/meta/config' branch of
   * the specified project. Parameters which are not set in the
   * '{@code <plugin-name>.config}' of this project are inherited from the
   * parent project's '{@code <plugin-name>.config}' files.
   *
   * E.g.: child project: [mySection "mySubsection"] myKey = childValue
   *
   * parent project: [mySection "mySubsection"] myKey = parentValue anotherKey =
   * someValue
   *
   * return: [mySection "mySubsection"] myKey = childValue anotherKey =
   * someValue
   *
   * @param projectName the name of the project for which the plugin
   *        configuration should be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the '{@code <plugin-name>.config}'
   *         file of the specified project with inheriting non-set parameters
   *         from the parent projects
   * @throws NoSuchProjectException thrown if the specified project does not
   *         exist
   */
  public Config getProjectPluginConfigWithInheritance(Project.NameKey projectName,
      String pluginName) throws NoSuchProjectException {
    return getPluginConfig(projectName, pluginName).getWithInheritance();
  }

  /**
   * Returns the configuration for the specified plugin that is stored in the
   * '{@code <plugin-name>.config}' file in the 'refs/meta/config' branch of
   * the specified project. Parameters which are not set in the
   * '{@code <plugin-name>.config}' of this project are inherited from the
   * parent project's '{@code <plugin-name>.config}' files.
   *
   * E.g.: child project: [mySection "mySubsection"] myKey = childValue
   *
   * parent project: [mySection "mySubsection"] myKey = parentValue anotherKey =
   * someValue
   *
   * return: [mySection "mySubsection"] myKey = childValue anotherKey =
   * someValue
   *
   * @param projectState the project for which the plugin configuration should
   *        be returned
   * @param pluginName the name of the plugin for which the configuration should
   *        be returned
   * @return the plugin configuration from the '{@code <plugin-name>.config}'
   *         file of the specified project with inheriting non-set parameters
   *         from the parent projects
   */
  public Config getProjectPluginConfigWithInheritance(ProjectState projectState,
      String pluginName) {
    return projectState.getConfig(pluginName + EXTENSION).getWithInheritance();
  }

  private ProjectLevelConfig getPluginConfig(Project.NameKey projectName,
      String pluginName) throws NoSuchProjectException {
    ProjectState projectState = projectCache.get(projectName);
    if (projectState == null) {
      throw new NoSuchProjectException(projectName);
    }
    return projectState.getConfig(pluginName + EXTENSION);
  }

  @Override
  public synchronized void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    pluginConfigs.remove(oldPlugin.getName());
  }
}
