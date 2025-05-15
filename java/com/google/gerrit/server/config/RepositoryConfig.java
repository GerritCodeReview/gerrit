// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;

@Singleton
public class RepositoryConfig {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String SECTION_NAME = "repository";
  static final String OWNER_GROUP_NAME = "ownerGroup";
  static final String DEFAULT_CONFIG_NAME = "defaultConfig";
  static final String DEFAULT_SUBMIT_TYPE_NAME = "defaultSubmitType";
  static final String BASE_PATH_NAME = "basePath";

  static final SubmitType DEFAULT_SUBMIT_TYPE = SubmitType.INHERIT;

  private final Config cfg;

  @Inject
  public RepositoryConfig(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
  }

  /**
   * Gets the default value that is configured for the given boolean project config that matches the
   * given project.
   *
   * <p>If no default value is configured, {@link DefaultBooleanProjectConfig.Value#FALSE} is
   * returned.
   */
  public DefaultBooleanProjectConfig.Value getDefault(
      Project.NameKey project, BooleanProjectConfig booleanProjectConfig) {
    Optional<DefaultBooleanProjectConfig.Value> configuredDefault =
        Arrays.stream(
                cfg.getStringList(SECTION_NAME, findSubSection(project.get()), DEFAULT_CONFIG_NAME))
            .map(DefaultBooleanProjectConfig::tryParse)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(
                defaultBooleanProjectConfig ->
                    defaultBooleanProjectConfig.section().equals(booleanProjectConfig.getSection())
                        && defaultBooleanProjectConfig
                            .subSection()
                            .equals(Optional.ofNullable(booleanProjectConfig.getSubSection()))
                        && defaultBooleanProjectConfig
                            .name()
                            .equals(booleanProjectConfig.getName()))
            .findFirst()
            .map(DefaultBooleanProjectConfig::defaultValue);

    if (configuredDefault.isPresent()) {
      if (configuredDefault.get().equals(DefaultBooleanProjectConfig.Value.FORCED)) {
        logger.atFine().log(
            "enforcing configured default %s=%s for project %s (settings for %s on project-level"
                + " are ignored)",
            booleanProjectConfig.format(),
            configuredDefault.get().name().toLowerCase(Locale.US),
            project,
            booleanProjectConfig.format());
      } else {
        logger.atFine().log(
            "applying configured default %s=%s for project %s (overrides for %s on project-level do"
                + " apply)",
            booleanProjectConfig.format(),
            configuredDefault.get().name().toLowerCase(Locale.US),
            project,
            booleanProjectConfig.format());
      }
    }

    return configuredDefault.orElse(DefaultBooleanProjectConfig.Value.FALSE);
  }

  public SubmitType getDefaultSubmitType(Project.NameKey project) {
    return cfg.getEnum(
        SECTION_NAME, findSubSection(project.get()), DEFAULT_SUBMIT_TYPE_NAME, DEFAULT_SUBMIT_TYPE);
  }

  public ImmutableList<String> getOwnerGroups(Project.NameKey project) {
    return ImmutableList.copyOf(
        cfg.getStringList(SECTION_NAME, findSubSection(project.get()), OWNER_GROUP_NAME));
  }

  @Nullable
  public Path getBasePath(Project.NameKey project) {
    String basePath = cfg.getString(SECTION_NAME, findSubSection(project.get()), BASE_PATH_NAME);
    return basePath != null ? Path.of(basePath) : null;
  }

  public ImmutableList<Path> getAllBasePaths() {
    return cfg.getSubsections(SECTION_NAME).stream()
        .map(sub -> cfg.getString(SECTION_NAME, sub, BASE_PATH_NAME))
        .filter(Objects::nonNull)
        .map(Paths::get)
        .collect(toImmutableList());
  }

  /**
   * Find the subsection to get repository configuration from.
   *
   * <p>Subsection can use the * pattern so if project name matches more than one section, return
   * the more precise one. E.g if the following subsections are defined:
   *
   * <pre>
   * [repository "somePath/*"]
   *   name = value
   * [repository "somePath/somePath/*"]
   *   name = value
   * </pre>
   *
   * and this method is called with "somePath/somePath/someProject" as project name, it will return
   * the subsection "somePath/somePath/*"
   *
   * @param project Name of the project
   * @return the name of the subsection, null if none is found
   */
  @Nullable
  private String findSubSection(String project) {
    return cfg.getSubsections(SECTION_NAME).stream()
        .filter(ss -> isMatch(ss, project))
        .max(comparing(String::length))
        .orElse(null);
  }

  private boolean isMatch(String subSection, String project) {
    return project.equals(subSection)
        || (subSection.endsWith("*")
            && project.startsWith(subSection.substring(0, subSection.length() - 1)));
  }
}
