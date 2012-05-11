package com.google.gerrit.common;

public class PluginDefinition {

  private final String name;
  private final String version;

  public PluginDefinition(final String name, final String version) {
    this.name = name;
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public String getVersion() {
    return version;
  }
}
