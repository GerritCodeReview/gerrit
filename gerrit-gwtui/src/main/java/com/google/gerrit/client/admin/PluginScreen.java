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

import static com.google.gerrit.common.PageLinks.ADMIN_PLUGINS;

import com.google.gerrit.client.ui.MenuScreen;

public abstract class PluginScreen extends MenuScreen {

  public PluginScreen() {
    setRequiresSignIn(true);

    link(Util.C.pluginTabInstalled(), ADMIN_PLUGINS);
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    setPageTitle(Util.C.plugins());
    display();
  }
}
