package com.google.gerrit.extensions.config;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/** Specifies a repository permission declared by a plugin. */
@ExtensionPoint
public abstract class PluginProjectPermissionDefinition implements PluginPermissionDefinition {}
