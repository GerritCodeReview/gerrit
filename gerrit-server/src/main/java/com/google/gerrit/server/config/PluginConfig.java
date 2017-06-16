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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class PluginConfig {
  private static final String PLUGIN = "plugin";

  private final String pluginName;
  private Config cfg;
  private final ProjectConfig projectConfig;

  public PluginConfig(String pluginName, Config cfg) {
    this(pluginName, cfg, null);
  }

  public PluginConfig(String pluginName, Config cfg, ProjectConfig projectConfig) {
    this.pluginName = pluginName;
    this.cfg = cfg;
    this.projectConfig = projectConfig;
  }

  PluginConfig withInheritance(ProjectState.Factory projectStateFactory) {
    if (projectConfig == null) {
      return this;
    }

    ProjectState state = projectStateFactory.create(projectConfig);
    ProjectState parent = Iterables.getFirst(state.parents(), null);
    if (parent != null) {
      PluginConfig parentPluginConfig =
          parent.getConfig().getPluginConfig(pluginName).withInheritance(projectStateFactory);
      Set<String> allNames = cfg.getNames(PLUGIN, pluginName);
      cfg = copyConfig(cfg);
      for (String name : parentPluginConfig.cfg.getNames(PLUGIN, pluginName)) {
        if (!allNames.contains(name)) {
          cfg.setStringList(
              PLUGIN,
              pluginName,
              name,
              Arrays.asList(parentPluginConfig.cfg.getStringList(PLUGIN, pluginName, name)));
        }
      }
    }
    return this;
  }

  private static Config copyConfig(Config cfg) {
    Config copiedCfg = new Config();
    try {
      copiedCfg.fromText(cfg.toText());
    } catch (ConfigInvalidException e) {
      // cannot happen
      throw new IllegalStateException(e);
    }
    return copiedCfg;
  }

  public String getString(String name) {
    return cfg.getString(PLUGIN, pluginName, name);
  }

  public String getString(String name, String defaultValue) {
    if (defaultValue == null) {
      return cfg.getString(PLUGIN, pluginName, name);
    }
    return MoreObjects.firstNonNull(cfg.getString(PLUGIN, pluginName, name), defaultValue);
  }

  public void setString(String name, String value) {
    if (Strings.isNullOrEmpty(value)) {
      cfg.unset(PLUGIN, pluginName, name);
    } else {
      cfg.setString(PLUGIN, pluginName, name, value);
    }
  }

  public String[] getStringList(String name) {
    return cfg.getStringList(PLUGIN, pluginName, name);
  }

  public void setStringList(String name, List<String> values) {
    if (values == null || values.isEmpty()) {
      cfg.unset(PLUGIN, pluginName, name);
    } else {
      cfg.setStringList(PLUGIN, pluginName, name, values);
    }
  }

  public int getInt(String name, int defaultValue) {
    return cfg.getInt(PLUGIN, pluginName, name, defaultValue);
  }

  public void setInt(String name, int value) {
    cfg.setInt(PLUGIN, pluginName, name, value);
  }

  public long getLong(String name, long defaultValue) {
    return cfg.getLong(PLUGIN, pluginName, name, defaultValue);
  }

  public void setLong(String name, long value) {
    cfg.setLong(PLUGIN, pluginName, name, value);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return cfg.getBoolean(PLUGIN, pluginName, name, defaultValue);
  }

  public void setBoolean(String name, boolean value) {
    cfg.setBoolean(PLUGIN, pluginName, name, value);
  }

  public <T extends Enum<?>> T getEnum(String name, T defaultValue) {
    return cfg.getEnum(PLUGIN, pluginName, name, defaultValue);
  }

  public <T extends Enum<?>> void setEnum(String name, T value) {
    cfg.setEnum(PLUGIN, pluginName, name, value);
  }

  public <T extends Enum<?>> T getEnum(T[] all, String name, T defaultValue) {
    return cfg.getEnum(all, PLUGIN, pluginName, name, defaultValue);
  }

  public void unset(String name) {
    cfg.unset(PLUGIN, pluginName, name);
  }

  public Set<String> getNames() {
    return cfg.getNames(PLUGIN, pluginName, true);
  }

  public GroupReference getGroupReference(String name) {
    return projectConfig.getGroup(GroupReference.extractGroupName(getString(name)));
  }

  public void setGroupReference(String name, GroupReference value) {
    setString(name, value.toConfigValue());
  }
}
