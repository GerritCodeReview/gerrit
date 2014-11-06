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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.ConfigEntries;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class ListPluginConfigs implements RestReadView<ProjectResource> {
  private final PluginConfigFactory cfgFactory;
  private final PluginLoader loader;

  @Inject
  ListPluginConfigs(PluginConfigFactory cfgFactory, PluginLoader loader) {
    this.cfgFactory = cfgFactory;
    this.loader = loader;
  }

  @Override
  public List<ConfigEntries> apply(ProjectResource rsrc) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    if (!rsrc.getControl().isOwner()) {
      throw new ResourceNotFoundException(rsrc.getName());
    }
    List<ConfigEntries> list = new ArrayList<>();
    for (Plugin plugin : loader.getPlugins(true)) {
      String pluginName = plugin.getName();
      list.add(ConfigEntries.fromConfig(cfgFactory.getProjectPluginConfig(
          rsrc.getNameKey(), pluginName), pluginName));
    }
    return list;
  }
}
