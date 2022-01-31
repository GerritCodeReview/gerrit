// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.gerrit.extensions.api.access.PluginProjectPermission.PLUGIN_PERMISSION_NAME_PATTERN_STRING;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.PluginPermissionDefinition;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.regex.Pattern;

/** Utilities for plugin permissions. */
@Singleton
public final class PluginPermissionsUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PLUGIN_NAME_PATTERN_STRING = "[a-zA-Z0-9-]+";

  /**
   * Name pattern for a plugin non-capability permission stored in the config file.
   *
   * <p>This pattern requires a plugin declared permission to have a name in the access section of
   * {@code ProjectConfig} with a format like "plugin-{pluginName}-{permissionName}", which makes it
   * easier to tell if a config name represents a plugin permission or not. Note "-" isn't clear
   * enough for this purpose since some core permissions, e.g. "label-", also contain "-".
   */
  private static final Pattern PLUGIN_PERMISSION_NAME_IN_CONFIG_PATTERN =
      Pattern.compile(
          "^plugin-"
              + PLUGIN_NAME_PATTERN_STRING
              + "-"
              + PLUGIN_PERMISSION_NAME_PATTERN_STRING
              + "$");

  /** Name pattern for a Gerrit plugin. */
  private static final Pattern PLUGIN_NAME_PATTERN =
      Pattern.compile("^" + PLUGIN_NAME_PATTERN_STRING + "$");

  private final DynamicMap<CapabilityDefinition> capabilityDefinitions;
  private final DynamicMap<PluginProjectPermissionDefinition> pluginProjectPermissionDefinitions;

  @Inject
  PluginPermissionsUtil(
      DynamicMap<CapabilityDefinition> capabilityDefinitions,
      DynamicMap<PluginProjectPermissionDefinition> pluginProjectPermissionDefinitions) {
    this.capabilityDefinitions = capabilityDefinitions;
    this.pluginProjectPermissionDefinitions = pluginProjectPermissionDefinitions;
  }

  /**
   * Collects all the plugin declared capabilities.
   *
   * @return a map of plugin declared capabilities with "pluginName" as its keys and
   *     "pluginName-{permissionName}" as its values.
   */
  public ImmutableMap<String, String> collectPluginCapabilities() {
    return collectPermissions(capabilityDefinitions, "");
  }

  /**
   * Collects all the plugin declared project permissions.
   *
   * @return a map of plugin declared project permissions with "{pluginName}" as its keys and
   *     "plugin-{pluginName}-{permissionName}" as its values.
   */
  public ImmutableMap<String, String> collectPluginProjectPermissions() {
    return collectPermissions(pluginProjectPermissionDefinitions, "plugin-");
  }

  private static <T extends PluginPermissionDefinition>
      ImmutableMap<String, String> collectPermissions(DynamicMap<T> definitions, String prefix) {
    ImmutableMap.Builder<String, String> permissionIdNames = ImmutableMap.builder();

    for (Extension<T> extension : definitions) {
      String pluginName = extension.getPluginName();
      if (!PLUGIN_NAME_PATTERN.matcher(pluginName).matches()) {
        logger.atWarning().log(
            "Plugin name '%s' must match '%s' to use permissions; rename the plugin",
            pluginName, PLUGIN_NAME_PATTERN.pattern());
        continue;
      }

      String id = prefix + pluginName + "-" + extension.getExportName();
      permissionIdNames.put(id, extension.get().getDescription());
    }

    return permissionIdNames.build();
  }

  /**
   * Checks if a given name matches the plugin declared permission name pattern for configs.
   *
   * @param name a config name which may stand for a plugin permission.
   * @return whether the name matches the plugin permission name pattern for configs.
   */
  public static boolean isValidPluginPermission(String name) {
    return PLUGIN_PERMISSION_NAME_IN_CONFIG_PATTERN.matcher(name).matches();
  }
}
