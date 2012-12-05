// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.client.RecentlyAccessed;
import com.google.gerrit.client.projects.ProjectMap;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.reviewdb.client.Project;

import java.util.List;

public class RecentProjectsScreen extends ProjectListScreen {

  public RecentProjectsScreen() {
    setRequiresSignIn(true);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    final GerritCallback<ProjectMap> callback =
        new ScreenLoadCallback<ProjectMap>(this) {
          @Override
          protected void preDisplay(final ProjectMap result) {
            display(result);
          }
        };
    final List<Project.NameKey> projectNames =
        RecentlyAccessed.get().getProjects();
    if (!projectNames.isEmpty()) {
      ProjectMap.get(projectNames, callback);
    } else {
      callback.onSuccess(null);
    }
  }
}
