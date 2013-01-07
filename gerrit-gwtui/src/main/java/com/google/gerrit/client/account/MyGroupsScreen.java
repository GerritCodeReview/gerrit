// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.client.account;

import com.google.gerrit.client.admin.GroupTable;
import com.google.gerrit.client.groups.GroupMap;
import com.google.gerrit.client.rpc.ScreenLoadCallback;

public class MyGroupsScreen extends SettingsScreen {
  private GroupTable groups;

  @Override
  protected void onInitUI() {
    super.onInitUI();
    groups = new GroupTable(true /* hyperlink to admin */);
    add(groups);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    GroupMap.my(new ScreenLoadCallback<GroupMap>(this) {
      @Override
      protected void preDisplay(final GroupMap result) {
        groups.display(result);
        groups.finishDisplay();
      }});
  }
}
