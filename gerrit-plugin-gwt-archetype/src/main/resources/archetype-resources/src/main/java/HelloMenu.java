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

package ${package};

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.extensions.webui.TopMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Listen
public class HelloMenu implements TopMenu {
  public final static String MENU_ID = "hello_open-dialog-box";
  private final List<MenuEntry> menuEntries;

  public HelloMenu() {
    menuEntries = new ArrayList<TopMenu.MenuEntry>();
    menuEntries.add(new MenuEntry("Hello", Collections
        .singletonList(new MenuItem("Open Dialog Box", "", "", MENU_ID))));
  }

  @Override
  public List<MenuEntry> getEntries() {
    return menuEntries;
  }
}
