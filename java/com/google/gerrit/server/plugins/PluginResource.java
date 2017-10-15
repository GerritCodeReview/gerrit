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

import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;

public class PluginResource implements RestResource {
  public static final TypeLiteral<RestView<PluginResource>> PLUGIN_KIND =
      new TypeLiteral<RestView<PluginResource>>() {};

  private final Plugin plugin;
  private final String name;

  public PluginResource(Plugin plugin) {
    this.plugin = plugin;
    this.name = plugin.getName();
  }

  public PluginResource(String name) {
    this.plugin = null;
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public Plugin getPlugin() {
    return plugin;
  }
}
