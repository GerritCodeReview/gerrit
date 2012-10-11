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

package com.google.gerrit.client.admin;

import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.AccountScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.GroupList;

public class GroupListScreen extends AccountScreen {
  private GroupTable groups;

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.GROUP_SVC
        .visibleGroups(new ScreenLoadCallback<GroupList>(this) {
          @Override
          protected void preDisplay(GroupList result) {
            groups.display(result.getGroups());
            groups.finishDisplay();
          }
        });
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    setPageTitle(Util.C.groupListTitle());

    groups = new GroupTable(true /* hyperlink to admin */, PageLinks.ADMIN_GROUPS);
    add(groups);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    groups.setRegisterKeys(true);
  }
}
