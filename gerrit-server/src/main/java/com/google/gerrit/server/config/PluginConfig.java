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

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;

import org.eclipse.jgit.lib.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    List<ProjectState> tree =
        Lists.newArrayList(projectStateFactory.create(projectConfig).tree());
    Collections.reverse(tree);

    Config c = new Config();
    copy(c, tree.get(0));

    for (int i = 1; i < tree.size(); i++) {
      c = new Config(c);
      copy(c, tree.get(i));
    }
    cfg = c;

    return this;
  }

  private void copy(Config dst, ProjectState state) {
    Config src = state.getConfig().getPluginConfig(pluginName).cfg;
    for (String name : src.getNames(PLUGIN, pluginName)) {
      dst.setStringList(PLUGIN, pluginName, name,
          Arrays.asList(src.getStringList(PLUGIN, pluginName, name)));
    }
  }

  public String getString(String name) {
    return cfg.getString(PLUGIN, pluginName, name);
  }

  public String getString(String name, String defaultValue) {
    return Objects.firstNonNull(cfg.getString(PLUGIN, pluginName, name), defaultValue);
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
