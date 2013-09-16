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

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.lib.Config;

import java.util.Arrays;
import java.util.Set;

public class PluginConfig {
  private static final String PLUGIN = "plugin";

  private final String pluginName;
  private final Config cfg;
  private final ProjectConfig projectConfig;

  public PluginConfig(String pluginName, Config cfg) {
    this.pluginName = pluginName;
    this.cfg = cfg;
    this.projectConfig = null;
  }

  public PluginConfig(String pluginName,
      LinkedListMultimap<String, String> pluginConfigValues,
      ProjectConfig projectConfig) {
    this.pluginName = pluginName;
    this.projectConfig = projectConfig;
    this.cfg = new Config();

    for (String name : pluginConfigValues.keySet()) {
      cfg.setStringList(PLUGIN, pluginName, name,
          pluginConfigValues.get(name));
    }
  }

  PluginConfig withInheritance(ProjectState.Factory projectStateFactory) {
    if (projectConfig == null) {
      return this;
    }

    ProjectState state = projectStateFactory.create(projectConfig);
    ProjectState parent = Iterables.getFirst(state.parents(), null);
    if (parent != null) {
      PluginConfig parentPluginConfig =
          parent.getConfig().getPluginConfig(pluginName)
              .withInheritance(projectStateFactory);
      Set<String> allNames = cfg.getNames(PLUGIN, pluginName);
      for (String name : parentPluginConfig.cfg.getNames(PLUGIN, pluginName)) {
        if (!allNames.contains(name)) {
          cfg.setStringList(PLUGIN, pluginName, name, Arrays
              .asList(parentPluginConfig.cfg.getStringList(PLUGIN, pluginName, name)));
        }
      }
    }
    return this;
  }

  public String getString(String name) {
    return cfg.getString(PLUGIN, pluginName, name);
  }

  public String getString(String name, String defaultValue) {
    String value = cfg.getString(PLUGIN, pluginName, name);
    if (value != null) {
      return value;
    } else {
      return defaultValue;
    }
  }

  public String[] getStringList(String name) {
    return cfg.getStringList(PLUGIN, pluginName, name);
  }

  public int getInt(String name, int defaultValue) {
    return cfg.getInt(PLUGIN, pluginName, name, defaultValue);
  }

  public long getLong(String name, long defaultValue) {
    return cfg.getLong(PLUGIN, pluginName, name, defaultValue);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return cfg.getBoolean(PLUGIN, pluginName, name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(String name, T defaultValue) {
    return cfg.getEnum(PLUGIN, pluginName, name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(T[] all, String name, T defaultValue) {
    return cfg.getEnum(all, PLUGIN, pluginName, name, defaultValue);
  }
}
