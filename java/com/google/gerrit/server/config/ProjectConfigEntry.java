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

import static java.util.stream.Collectors.toList;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

@ExtensionPoint
public class ProjectConfigEntry {
  private final String displayName;
  private final String description;
  private final boolean inheritable;
  private final String defaultValue;
  private final ProjectConfigEntryType type;
  private final List<String> permittedValues;

  public ProjectConfigEntry(String displayName, String defaultValue) {
    this(displayName, defaultValue, false);
  }

  public ProjectConfigEntry(String displayName, String defaultValue, boolean inheritable) {
    this(displayName, defaultValue, inheritable, null);
  }

  public ProjectConfigEntry(
      String displayName, String defaultValue, boolean inheritable, String description) {
    this(displayName, defaultValue, ProjectConfigEntryType.STRING, null, inheritable, description);
  }

  public ProjectConfigEntry(String displayName, int defaultValue) {
    this(displayName, defaultValue, false);
  }

  public ProjectConfigEntry(String displayName, int defaultValue, boolean inheritable) {
    this(displayName, defaultValue, inheritable, null);
  }

  public ProjectConfigEntry(
      String displayName, int defaultValue, boolean inheritable, String description) {
    this(
        displayName,
        Integer.toString(defaultValue),
        ProjectConfigEntryType.INT,
        null,
        inheritable,
        description);
  }

  public ProjectConfigEntry(String displayName, long defaultValue) {
    this(displayName, defaultValue, false);
  }

  public ProjectConfigEntry(String displayName, long defaultValue, boolean inheritable) {
    this(displayName, defaultValue, inheritable, null);
  }

  public ProjectConfigEntry(
      String displayName, long defaultValue, boolean inheritable, String description) {
    this(
        displayName,
        Long.toString(defaultValue),
        ProjectConfigEntryType.LONG,
        null,
        inheritable,
        description);
  }

  // For inheritable boolean use 'LIST' type with InheritableBoolean
  public ProjectConfigEntry(String displayName, boolean defaultValue) {
    this(displayName, defaultValue, null);
  }

  // For inheritable boolean use 'LIST' type with InheritableBoolean
  public ProjectConfigEntry(String displayName, boolean defaultValue, String description) {
    this(
        displayName,
        Boolean.toString(defaultValue),
        ProjectConfigEntryType.BOOLEAN,
        null,
        false,
        description);
  }

  public ProjectConfigEntry(String displayName, String defaultValue, List<String> permittedValues) {
    this(displayName, defaultValue, permittedValues, false);
  }

  public ProjectConfigEntry(
      String displayName, String defaultValue, List<String> permittedValues, boolean inheritable) {
    this(displayName, defaultValue, permittedValues, inheritable, null);
  }

  public ProjectConfigEntry(
      String displayName,
      String defaultValue,
      List<String> permittedValues,
      boolean inheritable,
      String description) {
    this(
        displayName,
        defaultValue,
        ProjectConfigEntryType.LIST,
        permittedValues,
        inheritable,
        description);
  }

  public <T extends Enum<?>> ProjectConfigEntry(
      String displayName, T defaultValue, Class<T> permittedValues) {
    this(displayName, defaultValue, permittedValues, false);
  }

  public <T extends Enum<?>> ProjectConfigEntry(
      String displayName, T defaultValue, Class<T> permittedValues, boolean inheritable) {
    this(displayName, defaultValue, permittedValues, inheritable, null);
  }

  public <T extends Enum<?>> ProjectConfigEntry(
      String displayName,
      T defaultValue,
      Class<T> permittedValues,
      boolean inheritable,
      String description) {
    this(
        displayName,
        defaultValue.name(),
        ProjectConfigEntryType.LIST,
        Arrays.stream(permittedValues.getEnumConstants()).map(Enum::name).collect(toList()),
        inheritable,
        description);
  }

  public ProjectConfigEntry(
      String displayName,
      String defaultValue,
      ProjectConfigEntryType type,
      List<String> permittedValues,
      boolean inheritable,
      String description) {
    this.displayName = displayName;
    this.defaultValue = defaultValue;
    this.type = type;
    this.permittedValues = permittedValues;
    this.inheritable = inheritable;
    this.description = description;
    if (type == ProjectConfigEntryType.ARRAY && inheritable) {
      throw new ProvisionException("ARRAY doesn't support inheritable values");
    }
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getDescription() {
    return description;
  }

  public boolean isInheritable() {
    return inheritable;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public ProjectConfigEntryType getType() {
    return type;
  }

  public List<String> getPermittedValues() {
    return permittedValues;
  }

  /**
   * @param project project state.
   * @return whether the project is editable.
   */
  public boolean isEditable(ProjectState project) {
    return true;
  }

  /**
   * @param project project state.
   * @return any warning associated with the project.
   */
  public String getWarning(ProjectState project) {
    return null;
  }

  /**
   * Called before the project config is updated. To modify the value before the project config is
   * updated, override this method and return the modified value. Default implementation returns the
   * same value.
   *
   * @param configValue the original configValue that was entered.
   * @return the modified configValue.
   */
  public ConfigValue preUpdate(ConfigValue configValue) {
    return configValue;
  }

  /**
   * Called after reading the project config value. To modify the value before returning it to the
   * client, override this method and return the modified value. Default implementation returns the
   * same value.
   *
   * @param project the project.
   * @param value the actual value of the config entry (computed out of the configured value, the
   *     inherited value and the default value).
   * @return the modified value.
   */
  public String onRead(ProjectState project, String value) {
    return value;
  }

  /**
   * Called after reading the project config value of type ARRAY. To modify the values before
   * returning it to the client, override this method and return the modified values. Default
   * implementation returns the same values.
   *
   * @param project the project.
   * @param values the actual values of the config entry (computed out of the configured value, the
   *     inherited value and the default value).
   * @return the modified values.
   */
  public List<String> onRead(ProjectState project, List<String> values) {
    return values;
  }

  /**
   * Called after a project config is updated.
   *
   * @param project project name.
   * @param oldValue old entry value.
   * @param newValue new entry value.
   */
  public void onUpdate(Project.NameKey project, String oldValue, String newValue) {}

  /**
   * Called after a project config is updated.
   *
   * @param project project name.
   * @param oldValue old entry value.
   * @param newValue new entry value.
   */
  public void onUpdate(Project.NameKey project, Boolean oldValue, Boolean newValue) {}

  /**
   * Called after a project config is updated.
   *
   * @param project project name.
   * @param oldValue old entry value.
   * @param newValue new entry value.
   */
  public void onUpdate(Project.NameKey project, Integer oldValue, Integer newValue) {}

  /**
   * Called after a project config is updated.
   *
   * @param project project name.
   * @param oldValue old entry value.
   * @param newValue new entry value.
   */
  public void onUpdate(Project.NameKey project, Long oldValue, Long newValue) {}

  public static class UpdateChecker implements GitReferenceUpdatedListener {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final GitRepositoryManager repoManager;
    private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
    private final ProjectConfig.Factory projectConfigFactory;

    @Inject
    UpdateChecker(
        GitRepositoryManager repoManager,
        DynamicMap<ProjectConfigEntry> pluginConfigEntries,
        ProjectConfig.Factory projectConfigFactory) {
      this.repoManager = repoManager;
      this.pluginConfigEntries = pluginConfigEntries;
      this.projectConfigFactory = projectConfigFactory;
    }

    @Override
    public void onGitReferenceUpdated(Event event) {
      Project.NameKey p = Project.nameKey(event.getProjectName());
      if (!event.getRefName().equals(RefNames.REFS_CONFIG)) {
        return;
      }

      try {
        ProjectConfig oldCfg = parseConfig(p, event.getOldObjectId());
        ProjectConfig newCfg = parseConfig(p, event.getNewObjectId());
        if (oldCfg != null && newCfg != null) {
          for (Extension<ProjectConfigEntry> e : pluginConfigEntries) {
            ProjectConfigEntry configEntry = e.getProvider().get();
            String newValue = getValue(newCfg, e);
            String oldValue = getValue(oldCfg, e);
            if ((newValue == null && oldValue == null)
                || (newValue != null && newValue.equals(oldValue))) {
              return;
            }

            switch (configEntry.getType()) {
              case BOOLEAN:
                configEntry.onUpdate(p, toBoolean(oldValue), toBoolean(newValue));
                break;
              case INT:
                configEntry.onUpdate(p, toInt(oldValue), toInt(newValue));
                break;
              case LONG:
                configEntry.onUpdate(p, toLong(oldValue), toLong(newValue));
                break;
              case LIST:
              case STRING:
              case ARRAY:
              default:
                configEntry.onUpdate(p, oldValue, newValue);
            }
          }
        }
      } catch (IOException | ConfigInvalidException e) {
        logger.atSevere().withCause(e).log(
            "Failed to check if plugin config of project %s was updated.", p.get());
      }
    }

    private ProjectConfig parseConfig(Project.NameKey p, String idStr)
        throws IOException, ConfigInvalidException, RepositoryNotFoundException {
      ObjectId id = ObjectId.fromString(idStr);
      if (ObjectId.zeroId().equals(id)) {
        return null;
      }
      try (Repository repo = repoManager.openRepository(p)) {
        ProjectConfig pc = projectConfigFactory.create(p);
        pc.load(repo, id);
        return pc;
      }
    }

    private static String getValue(ProjectConfig cfg, Extension<ProjectConfigEntry> e) {
      String value =
          cfg.getPluginConfig(e.getPluginName()).asPluginConfig().getString(e.getExportName());
      if (value == null) {
        value = e.getProvider().get().getDefaultValue();
      }
      return value;
    }
  }

  private static Boolean toBoolean(String value) {
    return value != null ? Boolean.parseBoolean(value) : null;
  }

  private static Integer toInt(String value) {
    return value != null ? Integer.parseInt(value) : null;
  }

  private static Long toLong(String value) {
    return value != null ? Long.parseLong(value) : null;
  }
}
