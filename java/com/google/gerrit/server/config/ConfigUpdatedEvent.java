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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Config;

/**
 * This event is produced by {@link GerritServerConfigReloader} and forwarded to callers
 * implementing {@link GerritConfigListener}.
 *
 * <p>The event intends to:
 *
 * <p>1. Help the callers figure out if any action should be taken, depending on which entries are
 * updated in gerrit.config.
 *
 * <p>2. Provide the callers with a mechanism to accept/reject the entries of interest: @see
 * accept(Set<ConfigKey> entries), @see accept(String section), @see reject(Set<ConfigKey> entries)
 * (+ various overloaded versions of these)
 */
public class ConfigUpdatedEvent {
  public static final ImmutableMultimap<UpdateResult, ConfigUpdateEntry> NO_UPDATES =
      new ImmutableMultimap.Builder<UpdateResult, ConfigUpdateEntry>().build();
  private final Config oldConfig;
  private final Config newConfig;

  public ConfigUpdatedEvent(Config oldConfig, Config newConfig) {
    this.oldConfig = oldConfig;
    this.newConfig = newConfig;
  }

  public Config getOldConfig() {
    return this.oldConfig;
  }

  public Config getNewConfig() {
    return this.newConfig;
  }

  private String getString(ConfigKey key, Config config) {
    return config.getString(key.section(), key.subsection(), key.name());
  }

  public Multimap<UpdateResult, ConfigUpdateEntry> accept(ConfigKey entry) {
    return accept(Collections.singleton(entry));
  }

  public Multimap<UpdateResult, ConfigUpdateEntry> accept(Set<ConfigKey> entries) {
    return createUpdate(entries, UpdateResult.APPLIED);
  }

  public Multimap<UpdateResult, ConfigUpdateEntry> accept(String section) {
    Set<ConfigKey> entries = getEntriesFromSection(oldConfig, section);
    entries.addAll(getEntriesFromSection(newConfig, section));
    return createUpdate(entries, UpdateResult.APPLIED);
  }

  public Multimap<UpdateResult, ConfigUpdateEntry> reject(ConfigKey entry) {
    return reject(Collections.singleton(entry));
  }

  public Multimap<UpdateResult, ConfigUpdateEntry> reject(Set<ConfigKey> entries) {
    return createUpdate(entries, UpdateResult.REJECTED);
  }

  private static Set<ConfigKey> getEntriesFromSection(Config config, String section) {
    Set<ConfigKey> res = new LinkedHashSet<>();
    for (String name : config.getNames(section, true)) {
      res.add(ConfigKey.create(section, name));
    }
    for (String sub : config.getSubsections(section)) {
      for (String name : config.getNames(section, sub, true)) {
        res.add(ConfigKey.create(section, sub, name));
      }
    }
    return res;
  }

  private Multimap<UpdateResult, ConfigUpdateEntry> createUpdate(
      Set<ConfigKey> entries, UpdateResult updateResult) {
    Multimap<UpdateResult, ConfigUpdateEntry> updates = ArrayListMultimap.create();
    entries
        .stream()
        .filter(this::isValueUpdated)
        .map(e -> new ConfigUpdateEntry(e, getString(e, oldConfig), getString(e, newConfig)))
        .forEach(e -> updates.put(updateResult, e));
    return updates;
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
    return isValueUpdated(key.section(), key.subsection(), key.name());
  }

  public boolean isValueUpdated(String section, String name) {
    return isValueUpdated(section, null, name);
  }

  public boolean isEntriesUpdated(Set<ConfigKey> entries) {
    for (ConfigKey entry : entries) {
      if (isValueUpdated(entry.section(), entry.subsection(), entry.name())) {
        return true;
      }
    }
    return false;
  }

  public enum UpdateResult {
    APPLIED,
    REJECTED;

    @Override
    public String toString() {
      return StringUtils.capitalize(name().toLowerCase());
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

    /** Note: The toString() is used to format the output from @see ReloadConfig. */
    @Override
    public String toString() {
      switch (getUpdateType()) {
        case ADDED:
          return String.format("+ %s = %s", key, newVal);
        case MODIFIED:
          return String.format("* %s = [%s => %s]", key, oldVal, newVal);
        case REMOVED:
          return String.format("- %s = %s", key, oldVal);
        case UNMODIFIED:
          return String.format("  %s = %s", key, newVal);
        default:
          throw new IllegalStateException("Unexpected UpdateType: " + getUpdateType().name());
      }
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
