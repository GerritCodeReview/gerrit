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

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.ui.MenuScreen;
import com.google.gerrit.common.PageLinks;

public class ProjectOptionsScreen extends MenuScreen {
  public static final String VIEW = "view";
  public static final String DELETE_EMPTY = "delete_empty";
  public static final String UPDATE_STATUS = "update_status";

  public ProjectOptionsScreen() {
    link(Util.C.projectAdminTabView(), PageLinks.ADMIN_PROJECTS);
    if (Gerrit.isSignedIn()) {
      link(Util.C.projectAdminTabDeleteEmpty(),
          PageLinks.ADMIN_DELETE_EMPTY_PROJECT);
      link(Util.C.projectAdminTabChangeStatus(),
          PageLinks.ADMIN_CHANGE_STATUS_PROJECT);
    }
  }

  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.projectListTitle());
  }
}
