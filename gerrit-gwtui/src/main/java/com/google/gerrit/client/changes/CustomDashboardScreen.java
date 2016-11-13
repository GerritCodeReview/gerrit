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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.ui.Screen;

public class CustomDashboardScreen extends Screen implements ChangeListScreen {
  private DashboardTable table;
  private String params;

  public CustomDashboardScreen(String params) {
    this.params = params;
  }

  @Override
  protected void onInitUI() {
    table =
        new DashboardTable(this, params) {
          @Override
          public void finishDisplay() {
            super.finishDisplay();
            display();
          }
        };

    super.onInitUI();

    String title = table.getTitle();
    if (title != null) {
      setWindowTitle(title);
      setPageTitle(title);
    }

    add(table);
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }
}
