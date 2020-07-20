// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

@AutoValue
public abstract class PluginConfig {
  private static final String PLUGIN = "plugin";

  protected abstract String pluginName();

  protected abstract Config cfg();

  protected abstract Optional<CachedProjectConfig> projectConfig();

  /** Mappings parsed from {@code groups} files. */
  protected abstract ImmutableMap<AccountGroup.UUID, GroupReference> groupReferences();

  public static PluginConfig create(
      String pluginName, Config cfg, @Nullable CachedProjectConfig projectConfig) {
    ImmutableMap.Builder<AccountGroup.UUID, GroupReference> groupReferences =
        ImmutableMap.builder();
    if (projectConfig != null) {
      groupReferences.putAll(projectConfig.getGroups());
    }
    return new AutoValue_PluginConfig(
        pluginName, copyConfig(cfg), Optional.ofNullable(projectConfig), groupReferences.build());
  }

  PluginConfig withInheritance(ProjectState.Factory projectStateFactory) {
    checkState(projectConfig().isPresent(), "no project config provided");

    ProjectState state = projectStateFactory.create(projectConfig().get());
    ProjectState parent = Iterables.getFirst(state.parents(), null);
    if (parent == null) {
      return this;
    }

    Map<AccountGroup.UUID, GroupReference> groupReferences = new HashMap<>();
    groupReferences.putAll(groupReferences());
    PluginConfig parentPluginConfig =
        parent.getPluginConfig(pluginName()).withInheritance(projectStateFactory);
    Set<String> allNames = cfg().getNames(PLUGIN, pluginName());
    Config newCfg = copyConfig(cfg());
    for (String name : parentPluginConfig.cfg().getNames(PLUGIN, pluginName())) {
      if (!allNames.contains(name)) {
        List<String> values =
            Arrays.asList(parentPluginConfig.cfg().getStringList(PLUGIN, pluginName(), name));
        for (String value : values) {
          Optional<GroupReference> groupRef =
              parentPluginConfig
                  .projectConfig()
                  .get()
                  .getGroupByName(GroupReference.extractGroupName(value));
          if (groupRef.isPresent()) {
            groupReferences.putIfAbsent(groupRef.get().getUUID(), groupRef.get());
          }
        }
        newCfg.setStringList(PLUGIN, pluginName(), name, values);
      }
    }
    return new AutoValue_PluginConfig(
        pluginName(), newCfg, projectConfig(), ImmutableMap.copyOf(groupReferences));
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
    return cfg().getString(PLUGIN, pluginName(), name);
  }

  public String getString(String name, String defaultValue) {
    if (defaultValue == null) {
      return cfg().getString(PLUGIN, pluginName(), name);
    }
    return MoreObjects.firstNonNull(cfg().getString(PLUGIN, pluginName(), name), defaultValue);
  }

  public String[] getStringList(String name) {
    return cfg().getStringList(PLUGIN, pluginName(), name);
  }

  public int getInt(String name, int defaultValue) {
    return cfg().getInt(PLUGIN, pluginName(), name, defaultValue);
  }

  public long getLong(String name, long defaultValue) {
    return cfg().getLong(PLUGIN, pluginName(), name, defaultValue);
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    return cfg().getBoolean(PLUGIN, pluginName(), name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(String name, T defaultValue) {
    return cfg().getEnum(PLUGIN, pluginName(), name, defaultValue);
  }

  public <T extends Enum<?>> T getEnum(T[] all, String name, T defaultValue) {
    return cfg().getEnum(all, PLUGIN, pluginName(), name, defaultValue);
  }

  public Set<String> getNames() {
    return cfg().getNames(PLUGIN, pluginName(), true);
  }

  public Optional<GroupReference> getGroupReference(String name) {
    String exactName = GroupReference.extractGroupName(getString(name));
    return groupReferences().values().stream().filter(g -> exactName.equals(g.getName())).findAny();
  }

  /** Mutable representation of {@link PluginConfig}. Used for updates. */
  public static class Update {
    private final String pluginName;
    private Config cfg;
    private final Optional<ProjectConfig> projectConfig;

    public Update(String pluginName, Config cfg, Optional<ProjectConfig> projectConfig) {
      this.pluginName = pluginName;
      this.cfg = cfg;
      this.projectConfig = projectConfig;
    }

    @VisibleForTesting
    public static Update forTest(String pluginName, Config cfg) {
      return new Update(pluginName, cfg, Optional.empty());
    }

    public PluginConfig asPluginConfig() {
      return PluginConfig.create(
          pluginName, cfg, projectConfig.map(ProjectConfig::getCacheable).orElse(null));
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

    public Set<String> getNames() {
      return cfg.getNames(PLUGIN, pluginName, true);
    }

    public void setString(String name, String value) {
      if (Strings.isNullOrEmpty(value)) {
        cfg.unset(PLUGIN, pluginName, name);
      } else {
        cfg.setString(PLUGIN, pluginName, name, value);
      }
    }

    public void setStringList(String name, List<String> values) {
      if (values == null || values.isEmpty()) {
        cfg.unset(PLUGIN, pluginName, name);
      } else {
        cfg.setStringList(PLUGIN, pluginName, name, values);
      }
    }

    public void setInt(String name, int value) {
      cfg.setInt(PLUGIN, pluginName, name, value);
    }

    public void setLong(String name, long value) {
      cfg.setLong(PLUGIN, pluginName, name, value);
    }

    public void setBoolean(String name, boolean value) {
      cfg.setBoolean(PLUGIN, pluginName, name, value);
    }

    public <T extends Enum<?>> void setEnum(String name, T value) {
      cfg.setEnum(PLUGIN, pluginName, name, value);
    }

    public void unset(String name) {
      cfg.unset(PLUGIN, pluginName, name);
    }

    public void setGroupReference(String name, GroupReference value) {
      checkState(projectConfig.isPresent(), "no project config provided");
      GroupReference groupRef = projectConfig.get().resolve(value);
      setString(name, groupRef.toConfigValue());
    }
  }
}
