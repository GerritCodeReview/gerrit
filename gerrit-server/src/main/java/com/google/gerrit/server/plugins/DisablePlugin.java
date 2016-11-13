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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.plugins.DisablePlugin.Input;
import com.google.gerrit.server.plugins.ListPlugins.PluginInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@Singleton
class DisablePlugin implements RestModifyView<PluginResource, Input> {
  static class Input {}

  private final PluginLoader loader;

  @Inject
  DisablePlugin(PluginLoader loader) {
    this.loader = loader;
  }

  @Override
  public PluginInfo apply(PluginResource resource, Input input) throws MethodNotAllowedException {
    if (!loader.isRemoteAdminEnabled()) {
      throw new MethodNotAllowedException("remote plugin administration is disabled");
    }
    String name = resource.getName();
    loader.disablePlugins(ImmutableSet.of(name));
    return new PluginInfo(loader.get(name));
  }
}
