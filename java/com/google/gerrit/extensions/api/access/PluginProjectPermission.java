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

package com.google.gerrit.extensions.api.access;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import java.util.Objects;
import java.util.regex.Pattern;

/** Repository permissions defined by plugins. */
public final class PluginProjectPermission implements CoreOrPluginProjectPermission {
  public static final String PLUGIN_PERMISSION_NAME_PATTERN_STRING = "[a-zA-Z]+";
  private static final Pattern PLUGIN_PERMISSION_PATTERN =
      Pattern.compile("^" + PLUGIN_PERMISSION_NAME_PATTERN_STRING + "$");

  private final String pluginName;
  private final String permission;

  public PluginProjectPermission(String pluginName, String permission) {
    requireNonNull(pluginName, "pluginName");
    requireNonNull(permission, "permission");
    checkArgument(
        isValidPluginPermissionName(permission), "invalid plugin permission name: %s", permission);

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
  public String permissionName() {
    return pluginName + "~" + permission;
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
    return MoreObjects.toStringHelper(this)
        .add("pluginName", pluginName)
        .add("permission", permission)
        .toString();
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
