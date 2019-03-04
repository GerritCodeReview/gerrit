package com.google.gerrit.extensions.api.access;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import java.util.Objects;
import java.util.regex.Pattern;

/** Repository permissions defined by plugins. */
public class PluginProjectPermission implements CoreOrPluginProjectPermission {
  public static final String PLUGIN_PERMISSION_NAME_PATTERN_STRING = "[a-zA-Z]+";
  private static final Pattern PLUGIN_PERMISSION_PATTERN =
      Pattern.compile("^" + PLUGIN_PERMISSION_NAME_PATTERN_STRING + "$");

  private final String pluginName;
  private final String permission;

  public PluginProjectPermission(String pluginName, String permission) {
    requireNonNull(pluginName, "pluginName");
    requireNonNull(permission, "permission");
    checkArgument(isValidPluginPermissionName(permission), "invalid permission name");

    this.pluginName = pluginName;
    this.permission = permission;
  }

  public String pluginName() {
    return pluginName;
  }

  public String permission() {
    return permission;
  }

  @Override
  public String describeForException() {
    return permission + " for plugin " + pluginName;
  }

  @Override
  public int hashCode() {
    return Objects.hash(pluginName, permission);
  }

  @Override
  public boolean equals(Object other) {
    if (other instanceof PluginProjectPermission) {
      PluginProjectPermission b = (PluginProjectPermission) other;
      return pluginName.equals(b.pluginName) && permission.equals(b.permission);
    }
    return false;
  }

  @Override
  public String toString() {
    return "PluginPermission[plugin=" + pluginName + ", permission=" + permission + ']';
  }

  /**
   * Checks if a given name is valid to be used for plugin permissions.
   *
   * @param name a name string.
   * @return whether the name is valid as a plugin permission.
   */
  private static boolean isValidPluginPermissionName(String name) {
    return PLUGIN_PERMISSION_PATTERN.matcher(name).matches();
  }
}
