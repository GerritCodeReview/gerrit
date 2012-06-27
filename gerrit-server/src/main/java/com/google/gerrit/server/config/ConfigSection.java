package com.google.gerrit.server.config;

import org.eclipse.jgit.lib.Config;

/** Provides access to one section from {@link Config} */
public class ConfigSection {

  private final Config cfg;
  private final String section;

  public ConfigSection(Config cfg, String section) {
    this.cfg = cfg;
    this.section = section;
  }

  public String optional(String name) {
    return cfg.getString(section, null, name);
  }

  public String required(String name) {
    return ConfigUtil.getRequired(cfg, null, name);
  }
}
