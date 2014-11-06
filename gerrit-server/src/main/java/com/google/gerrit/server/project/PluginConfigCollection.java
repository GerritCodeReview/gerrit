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

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.gerrit.server.plugins.PluginLoader;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class PluginConfigCollection implements
    ChildCollection<ProjectResource, PluginConfigResource> {
  private final DynamicMap<RestView<PluginConfigResource>> views;
  private final Provider<ListPluginConfigs> list;
  private final PluginLoader loader;

  @Inject
  PluginConfigCollection(
      DynamicMap<RestView<PluginConfigResource>> views,
      Provider<ListPluginConfigs> list,
      PluginLoader loader) {
    this.views = views;
    this.list = list;
    this.loader = loader;
  }

  @Override
  public RestView<ProjectResource> list() throws ResourceNotFoundException,
      AuthException {
    return list.get();
  }

  @Override
  public PluginConfigResource parse(ProjectResource rsrc, IdString id)
      throws ResourceNotFoundException, Exception {
    String pluginName = id.get();
    String fileName = pluginName + ".config";
    ProjectLevelConfig cfg = loader.get(pluginName) != null ?
        rsrc.getControl().getProjectState().getConfig(fileName) :
          null;
    return new PluginConfigResource(rsrc, pluginName, fileName, cfg);
  }

  @Override
  public DynamicMap<RestView<PluginConfigResource>> views() {
    return views;
  }
}
