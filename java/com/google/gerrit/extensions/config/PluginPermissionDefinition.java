package com.google.gerrit.extensions.config;

/** Specifies a permission declared by a plugin. */
public interface PluginPermissionDefinition {
  /**
   * Gets the description of a permission declared by a plugin.
   *
   * @return description of the permission.
   */
  String getDescription();
}
