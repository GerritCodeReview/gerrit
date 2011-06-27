// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.client.admin;

import static com.google.gerrit.client.Dispatcher.toProjectAdmin;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.reviewdb.Project;

public abstract class ProjectScreen extends MenuScreen {
  public static final String INFO = "info";
  public static final String BRANCH = "branches";
  public static final String ACCESS = "access";
  public static final String REF_MERGE_STRATEGY_TAB = "merge_strategies";

  private final Project.NameKey name;

  public ProjectScreen(final Project.NameKey toShow) {
    name = toShow;

    final boolean isWild = toShow.equals(Gerrit.getConfig().getWildProject());

    link(Util.C.projectAdminTabGeneral(), toProjectAdmin(name, INFO));
    if (!isWild) {
      link(Util.C.projectAdminTabBranches(), toProjectAdmin(name, BRANCH));
    }
    link(Util.C.projectAdminTabAccess(), toProjectAdmin(name, ACCESS));
    link(Util.C.projectAdminTabRefMergeStrategy(), toProjectAdmin(name,
        REF_MERGE_STRATEGY_TAB));
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
