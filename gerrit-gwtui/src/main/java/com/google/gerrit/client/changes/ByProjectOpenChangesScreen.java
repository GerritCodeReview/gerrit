// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.reviewdb.Project;


public class ByProjectOpenChangesScreen extends PagedSingleListScreen {
  private final Project.NameKey projectKey;

  public ByProjectOpenChangesScreen(final Project.NameKey proj,
      final String positionToken) {
    super("project,open," + proj.toString(), positionToken);
    projectKey = proj;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.M.changesOpenInProject(projectKey.get()));
  }

  @Override
  protected void loadPrev() {
    Util.LIST_SVC.byProjectOpenPrev(projectKey, pos, pageSize, loadCallback());
  }

  @Override
  protected void loadNext() {
    Util.LIST_SVC.byProjectOpenNext(projectKey, pos, pageSize, loadCallback());
  }
}
