package com.google.gerrit.server.permissions;

import static com.google.gerrit.extensions.api.access.PluginProjectPermission.PLUGIN_PERMISSION_NAME_PATTERN_STRING;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.PluginPermissionDefinition;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Provider;
import java.util.Map;
import java.util.regex.Pattern;

/** Utilities for plugin permissions. */
public class PluginPermissionsUtil {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String PLUGIN_NAME_PATTERN_STRING = "[a-zA-Z0-9-]+";

  /**
   * Name pattern for a plugin non-capability permission stored in the config file.
   *
   * <p>This pattern requires a plugin declared permission has a name in the access section of
   * {@code ProjectConfig} with a format like "plugin-{pluginName}-{permissionName}", which makes it
   * easier to tell if a config name represents a plugin permission or not. Note "-" isn't a clear
   * enough for this purpose since some core permissions, e.g. "label-", also contains "-".
   *
   * <p>Plugin declared capabilities have to keep the old format "{plugin-name}-capability" before a
   * data migration is done.
   */
  private static final Pattern PLUGIN_PERMISSION_NAME_IN_CONFIG_PATTERN =
      Pattern.compile(
          "^"
              + "plugin-"
              + PLUGIN_NAME_PATTERN_STRING
              + "-"
              + PLUGIN_PERMISSION_NAME_PATTERN_STRING
              + "$");

  /** Name pattern for a Gerrit plugin. */
  private static final Pattern PLUGIN_NAME_PATTERN =
      Pattern.compile("^" + PLUGIN_NAME_PATTERN_STRING + "$");

  /**
   * Collects all the plugin declared capabilities.
   *
   * @param definitions A map of capability definitions.
   * @return a map of plugin declared capabilities with "pluginName" as its keys and
   *     "pluginName-{permissionName}" as its values.
   */
  public static ImmutableMap<String, String> collectPluginCapabilities(
      DynamicMap<CapabilityDefinition> definitions) {
    return collectPermissions(definitions, "");
  }

  /**
   * Collects all the plugin declared project permissions.
   *
   * @param definitions A map of project permission definitions.
   * @return a map of plugin declared project permissions with "{pluginName}" as its keys and
   *     "plugin-{pluginName}-{permissionName}" as its values.
   */
  public static ImmutableMap<String, String> collectPluginProjectPermissions(
      DynamicMap<PluginProjectPermissionDefinition> definitions) {
    return collectPermissions(definitions, "plugin-");
  }

  private static <T extends PluginPermissionDefinition>
      ImmutableMap<String, String> collectPermissions(DynamicMap<T> definitions, String prefix) {
    ImmutableMap.Builder<String, String> permissionIdNames = ImmutableMap.builder();
    for (String pluginName : definitions.plugins()) {
      if (!PLUGIN_NAME_PATTERN.matcher(pluginName).matches()) {
        logger.atWarning().log(
            "Plugin name '%s' must match '%s' to use capabilities; rename the plugin",
            pluginName, PLUGIN_NAME_PATTERN.pattern());
        continue;
      }

      for (Map.Entry<String, Provider<T>> entry : definitions.byPlugin(pluginName).entrySet()) {
        String id = String.format("%s%s-%s", prefix, pluginName, entry.getKey());
        permissionIdNames.put(id, entry.getValue().get().getDescription());
      }
    }

    return permissionIdNames.build();
  }

  /**
   * Checks if a given name matches the plugin declared permission name pattern for configs.
   *
   * @param name a config name which may stand for a plugin permission.
   * @return whether the name matches the plugin permission name pattern for configs.
   */
  public static boolean isPluginPermission(String name) {
    return PLUGIN_PERMISSION_NAME_IN_CONFIG_PATTERN.matcher(name).matches();
  }

  private PluginPermissionsUtil() {}
}
