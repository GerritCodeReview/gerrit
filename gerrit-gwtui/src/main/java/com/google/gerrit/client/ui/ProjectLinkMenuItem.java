// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.Dispatcher;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.reviewdb.client.Project;

public class ProjectLinkMenuItem extends LinkMenuItem {
  protected final String panel;

  public ProjectLinkMenuItem(String text, String panel) {
    super(text, "");
    this.panel = panel;
  }

  @Override
  public void onScreenLoad(ScreenLoadEvent event) {
    Screen screen = event.getScreen();
    Project.NameKey projectKey;
    if (screen instanceof ProjectScreen) {
      projectKey = ((ProjectScreen) screen).getProjectKey();
    } else {
      projectKey = ProjectScreen.getSavedKey();
    }

    if (projectKey != null) {
      setVisible(true);
      onScreenLoad(projectKey);
    } else {
      setVisible(false);
    }
    super.onScreenLoad(event);
  }

  protected void onScreenLoad(Project.NameKey project) {
    setTargetHistoryToken(Dispatcher.toProjectAdmin(project, panel));
  }
}
