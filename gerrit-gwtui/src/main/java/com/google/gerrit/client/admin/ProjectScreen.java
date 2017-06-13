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

import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.reviewdb.client.Project;

public abstract class ProjectScreen extends Screen {
  public static final String INFO = "info";
  public static final String BRANCHES = "branches";
  public static final String ACCESS = "access";
  public static final String DASHBOARDS = "dashboards";
  public static final String TAGS = "tags";

  protected static String savedPanel;
  protected static Project.NameKey savedKey;

  public static String getSavedPanel() {
    return savedPanel;
  }

  public static Project.NameKey getSavedKey() {
    return savedKey;
  }

  private final Project.NameKey name;

  public ProjectScreen(Project.NameKey toShow) {
    name = toShow;
  }

  public Project.NameKey getProjectKey() {
    return name;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    if (name != null) {
      setPageTitle(AdminMessages.I.project(name.get()));
    }
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    savedKey = name;
  }
}
