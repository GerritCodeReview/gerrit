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

package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.PluginName;

public abstract class MyPluginLifecycleListener implements
    PluginLifecycleListener {

  private final String plugin;

  public MyPluginLifecycleListener(@PluginName String plugin) {
    this.plugin = plugin;
  }

  @Override
  public final void onLoad(String plugin) {
    if (this.plugin.equals(plugin)) {
      onLoad();
    }
  }

  @Override
  public final void onUnload(String plugin) {
    if (this.plugin.equals(plugin)) {
      onUnload();
    }
  }

  public abstract void onLoad();
  public abstract void onUnload();
}
