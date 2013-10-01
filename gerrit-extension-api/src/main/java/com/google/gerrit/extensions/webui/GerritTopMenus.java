package com.google.gerrit.extensions.webui;

public enum GerritTopMenus {
  ALL("All"), MY("My"), DIFFERENCES("Differences"), PROJECTS("Projects"),
  PEOPLE("People"), PLUGINS("Plugins"), DOCUMENTATION("Documentation");

  public final String menuName;

  private GerritTopMenus(String name) {
    menuName = name;
  }
}