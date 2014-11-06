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

import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.git.ProjectLevelConfig;
import com.google.inject.TypeLiteral;

public class PluginConfigResource extends ProjectResource {
  public static final TypeLiteral<RestView<PluginConfigResource>> PLUGIN_CONFIG_KIND =
      new TypeLiteral<RestView<PluginConfigResource>>() {};
  private final String pluginName;
  private final String fileName;
  private final ProjectLevelConfig config;

  PluginConfigResource(ProjectResource project, String pluginName,
      String fileName, ProjectLevelConfig config) {
    super(project);
    this.pluginName = pluginName;
    this.fileName = fileName;
    this.config = config;
  }

  public String getPluginName() {
    return this.pluginName;
  }

  public String getFileName() {
    return this.fileName;
  }

  public ProjectLevelConfig getConfig() {
    return this.config;
  }
}
