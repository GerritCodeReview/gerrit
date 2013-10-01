package com.google.gerrit.extensions.webui;

public enum GerritTopMenu {
  ALL("All"), MY("My"), DIFFERENCES("Differences"), PROJECTS("Projects"),
  PEOPLE("People"), PLUGINS("Plugins"), DOCUMENTATION("Documentation");

  public final String menuName;

  private GerritTopMenu(String name) {
    menuName = name;
  }
}