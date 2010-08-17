// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.gerrit.client.admin;

import static com.google.gerrit.client.Dispatcher.toProjectAdmin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.reviewdb.Project;

public abstract class ProjectScreen extends MenuScreen {
  public static final String INFO = "info";
  public static final String BRANCH = "branches";
  public static final String ACCESS = "access";
  public static final String REF_MERGE_STRATEGY_TAB = "merge strategies";

  private final Project.NameKey name;

  public ProjectScreen(final Project.NameKey toShow) {
    name = toShow;

    final boolean isWild = toShow.equals(Gerrit.getConfig().getWildProject());

    link(Util.C.projectAdminTabGeneral(), toProjectAdmin(name, INFO));
    if (!isWild) {
      link(Util.C.projectAdminTabBranches(), toProjectAdmin(name, BRANCH));
    }
    link(Util.C.projectAdminTabAccess(), toProjectAdmin(name, ACCESS));
    link(Util.C.projectAdminTabRefMergeStrategy(), toProjectAdmin(name, REF_MERGE_STRATEGY_TAB));
  }

  protected Project.NameKey getProjectKey() {
    return name;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.M.project(name.get()));
  }
}
