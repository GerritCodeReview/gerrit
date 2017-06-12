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

package com.google.gerrit.server.plugins;

import static com.google.gerrit.server.plugins.PluginResource.PLUGIN_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class PluginRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    bind(PluginsCollection.class);
    DynamicMap.mapOf(binder(), PLUGIN_KIND);
    put(PLUGIN_KIND).to(InstallPlugin.Overwrite.class);
    delete(PLUGIN_KIND).to(DisablePlugin.class);
    get(PLUGIN_KIND, "status").to(GetStatus.class);
    post(PLUGIN_KIND, "disable").to(DisablePlugin.class);
    post(PLUGIN_KIND, "enable").to(EnablePlugin.class);
    post(PLUGIN_KIND, "reload").to(ReloadPlugin.class);
  }
}
