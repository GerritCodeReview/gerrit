package com.google.gerrit.server.plugins;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class PluginDependency {
  public static PluginDependency createHardDependency(String name) {
    return new AutoValue_PluginDependency(name, PluginDependency.Type.HARD);
  }

  public static PluginDependency createSoftDependency(String name) {
    return new AutoValue_PluginDependency(name, PluginDependency.Type.SOFT);
  }

  public abstract String name();

  public abstract PluginDependency.Type type();

  enum Type {
    SOFT,
    HARD
  }
}
