// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.api.menus;

import com.google.gerrit.extensions.api.menus.TopMenus;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.restapi.config.ListTopMenus;
import com.google.inject.Inject;
import java.util.List;

public class TopMenusImpl implements TopMenus {
  private final ListTopMenus listTopMenus;

  @Inject
  public TopMenusImpl(ListTopMenus listTopMenus) {
    this.listTopMenus = listTopMenus;
  }

  @Override
  public List<TopMenu.MenuEntry> list() {
    return listTopMenus.apply(new ConfigResource()).value();
  }
}
