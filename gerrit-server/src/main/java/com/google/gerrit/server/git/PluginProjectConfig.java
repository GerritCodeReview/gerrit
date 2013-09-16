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

package com.google.gerrit.server.git;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.gerrit.server.project.ProjectState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PluginProjectConfig {
  private static final Logger log = LoggerFactory.getLogger(PluginProjectConfig.class);

  private final String pluginName;
  private final ProjectConfig config;
  private LinkedListMultimap<String, String> pluginConfig;

  public PluginProjectConfig(String pluginName, ProjectConfig config,
      LinkedListMultimap<String, String> pluginConfig) {
    this.pluginName = pluginName;
    this.config = config;
    this.pluginConfig = pluginConfig;
  }

  PluginProjectConfig withInheritance(
      ProjectState.Factory projectStateFactory) {
    ProjectState state = projectStateFactory.create(config);
    ProjectState parent = Iterables.getFirst(state.parents(), null);
    if (parent != null) {
      PluginProjectConfig parentPluginConfig =
          parent.getConfig().getPluginConfig(pluginName)
              .withInheritance(projectStateFactory);
      pluginConfig =
          LinkedListMultimap.create(pluginConfig);
      for (String key : parentPluginConfig.getAll().keySet()) {
        if (!pluginConfig.containsKey(key)) {
          pluginConfig.putAll(key, parentPluginConfig.getAll().get(key));
        }
      }
    }
    return this;
  }

  public LinkedListMultimap<String, String> getAll() {
    return pluginConfig;
  }

  public String getString(String name, String defaultValue) {
    return getValue(name, Functions.<String> identity(), defaultValue);
  }

  public String[] getPluginStringList(String name, String[] defaultValue) {
    List<String> values = getAll().get(name);
    if (values == null || values.isEmpty()) {
      return defaultValue;
    } else {
      return values.toArray(new String[values.size()]);
    }
  }

  public int getInt(String name, int defaultValue) {
    return getValue(name, new Function<String, Integer>() {
      @Override
      public Integer apply(String stringVal) {
        return Integer.parseInt(stringVal);
      }
    }, defaultValue);
  }

  public long getLong(String name, long defaultValue) {
    return getValue(name, new Function<String, Long>() {
      @Override
      public Long apply(String stringVal) {
        return Long.parseLong(stringVal);
      }
    }, defaultValue);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return getValue(name, new Function<String, Boolean>() {
      @Override
      public Boolean apply(String stringVal) {
        return Boolean.parseBoolean(stringVal);
      }
    }, defaultValue);
  }

  private <T> T getValue(String name, Function<String, T> converter,
      T defaultValue) {
    try {
      List<String> values = getAll().get(name);
      if (values == null || values.isEmpty()) {
        return defaultValue;
      } else {
        return converter.apply(values.get(0));
      }
    } catch (NumberFormatException e) {
      String valueType =
          defaultValue != null ? defaultValue.getClass().getName() : "";
      if (valueType.lastIndexOf('.') > 0) {
        valueType = valueType.substring(valueType.lastIndexOf('.'));
      }
      log.warn("value for " + name + " of plugin " + pluginName + " is not a " + valueType, e);
      return defaultValue;
    }
  }
}
