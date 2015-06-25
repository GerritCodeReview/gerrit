// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HelloMenu implements TopMenu {
  private final List<MenuEntry> menuEntries;

  @Inject
  public HelloMenu(@PluginName String pluginName) {
    menuEntries = new ArrayList<>();
    menuEntries.add(new MenuEntry("Hello", Collections
        .singletonList(new MenuItem("Hello Screen", "#/x/" + pluginName, ""))));
  }

  @Override
  public List<MenuEntry> getEntries() {
    return menuEntries;
  }
}
