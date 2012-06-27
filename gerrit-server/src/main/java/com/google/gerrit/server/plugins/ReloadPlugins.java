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

package com.google.gerrit.server.plugins;

import com.google.inject.Inject;

import java.util.List;

/** Reload the specified plugins, or rescan if none specified. */
public class ReloadPlugins {
  private final PluginLoader pluginLoader;

  @Inject
  protected ReloadPlugins(PluginLoader pluginLoader) {
    this.pluginLoader = pluginLoader;
  }

  public void reload(List<String> names) throws InvalidPluginException,
      PluginInstallException {
    if (names == null || names.isEmpty()) {
      pluginLoader.rescan();
    } else {
      pluginLoader.reload(names);
    }
  }
}
