// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.extensions.webui;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.client.GerritTopMenu;
import com.google.gerrit.extensions.client.MenuItem;
import java.util.List;

@ExtensionPoint
public interface TopMenu {
  class MenuEntry {
    public final String name;
    public final List<MenuItem> items;

    public MenuEntry(GerritTopMenu gerritMenu, List<MenuItem> items) {
      this(gerritMenu.menuName, items);
    }

    public MenuEntry(String name, List<MenuItem> items) {
      this.name = name;
      this.items = items;
    }
  }

  List<MenuEntry> getEntries();
}
