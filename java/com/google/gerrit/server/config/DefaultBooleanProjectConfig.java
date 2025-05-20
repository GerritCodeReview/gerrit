// Copyright (C) 2025 The Android Open Source Project
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

import com.google.auto.value.AutoValue;
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Default values for {@link com.google.gerrit.entities.BooleanProjectConfig}'s. */
@AutoValue
public abstract class DefaultBooleanProjectConfig {
  public enum Value {
    /**
     * The boolean config is {@code false} by default. Projects may override the default value in
     * their {@code project.config}.
     */
    FALSE,
    /**
     * The boolean config is {@code true} by default. Projects may override the default value in
     * their {@code project.config}.
     */
    TRUE,
    /**
     * The boolean config is {@code true} for all projects. Projects cannot override the default
     * value in their {@code project.config} (existing configurations for this parameter on project
     * level are ignored).
     */
    FORCED;

    static Value fromBooleanProjectConfig(BooleanProjectConfig config) {
      return config.getDefaultValue()
          ? DefaultBooleanProjectConfig.Value.TRUE
          : DefaultBooleanProjectConfig.Value.FALSE;
    }
  }

  /** Name of the section in {@code project.config} that defines the boolean project config. */
  public abstract String section();

  /**
   * Name of the subsection in {@code project.config} that defines the boolean project config. May
   * be unset if the boolean project config doesn't have a subsection.
   */
  public abstract Optional<String> subSection();

  /** Name of the boolean project config in {@code project.config}. */
  public abstract String name();

  /** The default value for the boolean project config. */
  public abstract Value defaultValue();

  /**
   * Tries to parse a string representation of a default boolean project config in the format {code
   * <section>.<name>=<defaultValue>} or {code <section>.<subSection>.<name>=<defaultValue>}.
   *
   * @param s the string representation of the default boolean project config that should be parsed
   * @return the parsed default boolean project config, or {@code Optional#empty()} if parsing the
   *     given string was not successful
   */
  public static Optional<DefaultBooleanProjectConfig> tryParse(String s) {
    if (s == null) {
      return Optional.empty();
    }

    // Split '<key>=<defaultValue>'.
    List<String> keyValueList = Splitter.on('=').splitToList(s);
    if (keyValueList.size() != 2) {
      return Optional.empty();
    }
    String key = keyValueList.get(0);
    String defaultValue = keyValueList.get(1);

    // Split '<section>.<name>' or '<section>.<subSection>.<name>'.
    List<String> keyList = Splitter.on('.').splitToList(key);
    if (keyList.size() != 2 && keyList.size() != 3) {
      return Optional.empty();
    }
    String section = keyList.get(0);
    String subSection = keyList.size() == 3 ? keyList.get(1) : null;
    String name = keyList.size() == 3 ? keyList.get(2) : keyList.get(1);

    // Check that none of the parsed parts is empty.
    if (section.length() == 0
        || (subSection != null && subSection.length() == 0)
        || name.length() == 0) {
      return Optional.empty();
    }

    // Parse the default value.
    com.google.common.base.Optional<Value> defaultValueAsEnum =
        Enums.getIfPresent(Value.class, defaultValue.toUpperCase(Locale.US));
    if (!defaultValueAsEnum.isPresent()) {
      return Optional.empty();
    }

    return Optional.of(create(section, subSection, name, defaultValueAsEnum.get()));
  }

  private static DefaultBooleanProjectConfig create(
      String section, @Nullable String subSection, String name, Value defaultValue) {
    return new AutoValue_DefaultBooleanProjectConfig(
        section, Optional.ofNullable(subSection), name, defaultValue);
  }
}
