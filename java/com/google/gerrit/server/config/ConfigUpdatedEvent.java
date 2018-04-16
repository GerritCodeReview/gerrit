// Copyright (C) 2018 The Android Open Source Project
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Config;

import com.google.common.collect.ImmutableList;

/**
 * Event produced by the GerritConfigListener, which allow callers to react on changes
 * in @GerritServerConfig.
 *
 * <p>Implementing classes are expected to do three things:
 *
 * <p>1. Investigate if any of the updated config values are of interest. The ConfigUpdatedEvent
 * provides helper methods for this purpose:
 *
 * <p>@see isSectionUpdated(String section), @see isValueUpdated(String section, String subsection,
 * String name) (+ various overloaded versions of these)
 *
 * <p>2. React to the Config changes (if any) and apply them,
 *
 * <p>3. accept/reject the entries of interest:
 *
 * <p>@see accept(Set<ConfigKey> entries), @see accept(String section), @see reject(Set<ConfigKey>
 * entries) (+ various overloaded versions of these)
 */
public class ConfigUpdatedEvent {
  private final Config oldConfig;
  private final Config newConfig;
  private final List<Update> updates;

  public ConfigUpdatedEvent(Config oldConfig, Config newConfig) {
    this.oldConfig = oldConfig;
    this.newConfig = newConfig;
    this.updates = new ArrayList<>();
  }

  public List<Update> getUpdates() {
    return ImmutableList.copyOf(updates);
  }

  public Config getOldConfig() {
    return this.oldConfig;
  }

  public Config getNewConfig() {
    return this.newConfig;
  }

  public void accept(ConfigKey entry) {
    accept(Collections.singleton(entry));
  }

  public void accept(Set<ConfigKey> entries) {
    addUpdate(entries, UpdateResult.ACCEPTED);
  }

  public void accept(String section) {
    Set<ConfigKey> entries = getEntriesFromSection(oldConfig, section);
    entries.addAll(getEntriesFromSection(newConfig, section));
    addUpdate(entries, UpdateResult.ACCEPTED);
  }

  public void reject(Set<ConfigKey> entries) {
    addUpdate(entries, UpdateResult.REJECTED);
  }

  private static Set<ConfigKey> getEntriesFromSection(Config config, String section) {
    Set<ConfigKey> res = new LinkedHashSet<>();
    for (String name : config.getNames(section, true)) {
      res.add(new ConfigKey(section, name));
    }
    for (String sub : config.getSubsections(section)) {
      for (String name : config.getNames(section, sub, true)) {
        res.add(new ConfigKey(section, sub, name));
      }
    }
    return res;
  }

  private void addUpdate(Set<ConfigKey> entries, UpdateResult updateResult) {
    Update update = new Update(updateResult);
    entries
        .stream()
        .filter(key -> isValueUpdated(key))
        .forEach(
            key -> {
              String oldValue = oldConfig.getString(key.section, key.subsection, key.name);
              String newValue = newConfig.getString(key.section, key.subsection, key.name);
              update.addConfigUpdate(new ConfigUpdateEntry(key, oldValue, newValue));
            });
    updates.add(update);
  }

  public boolean isSectionUpdated(String section) {
    Set<ConfigKey> entries = getEntriesFromSection(oldConfig, section);
    entries.addAll(getEntriesFromSection(newConfig, section));
    return isEntriesUpdated(entries);
  }

  public boolean isValueUpdated(String section, String subsection, String name) {
    return !Objects.equals(
        oldConfig.getString(section, subsection, name),
        newConfig.getString(section, subsection, name));
  }

  public boolean isValueUpdated(ConfigKey key) {
    return isValueUpdated(key.section, key.subsection, key.name);
  }

  public boolean isValueUpdated(String section, String name) {
    return isValueUpdated(section, null, name);
  }

  public boolean isEntriesUpdated(Set<ConfigKey> entries) {
    for (ConfigKey entry : entries) {
      if (isValueUpdated(entry.section, entry.subsection, entry.name)) {
        return true;
      }
    }
    return false;
  }

  public enum UpdateResult {
    ACCEPTED,
    REJECTED;

    @Override
    public String toString() {
      return StringUtils.capitalize(name().toLowerCase());
    }
  }

  /**
   * One Accepted/Rejected Update have one or more config updates (ConfigUpdateEntry) tied to it.
   */
  public static class Update {
    private UpdateResult result;
    private final Set<ConfigUpdateEntry> configUpdates;

    public Update(UpdateResult result) {
      this.configUpdates = new LinkedHashSet<>();
      this.result = result;
    }

    public UpdateResult getResult() {
      return result;
    }

    public List<ConfigUpdateEntry> getConfigUpdates() {
      return ImmutableList.copyOf(configUpdates);
    }

    public void addConfigUpdate(ConfigUpdateEntry entry) {
      this.configUpdates.add(entry);
    }
  }

  public enum ConfigEntryType {
    ADDED,
    REMOVED,
    MODIFIED,
    UNMODIFIED
  }

  public static class ConfigUpdateEntry {
    public final ConfigKey key;
    public final String oldVal;
    public final String newVal;

    public ConfigUpdateEntry(ConfigKey key, String oldVal, String newVal) {
      this.key = key;
      this.oldVal = oldVal;
      this.newVal = newVal;
    }

    @Override
    public String toString() {
      String res = "";
      switch (getUpdateType()) {
        case ADDED:
          res = String.format("+ %s = %s", key, newVal);
          break;
        case MODIFIED:
          res = String.format("* %s = [%s => %s]", key, oldVal, newVal);
          break;
        case REMOVED:
          res = String.format("- %s = %s", key, oldVal);
          break;
        case UNMODIFIED:
          res = String.format("  %s = %s", key, newVal);
          break;
      }
      return res;
    }

    public ConfigEntryType getUpdateType() {
      if (oldVal == null && newVal != null) {
        return ConfigEntryType.ADDED;
      }
      if (oldVal != null && newVal == null) {
        return ConfigEntryType.REMOVED;
      }
      if (Objects.equals(oldVal, newVal)) {
        return ConfigEntryType.UNMODIFIED;
      }
      return ConfigEntryType.MODIFIED;
    }
  }
}
