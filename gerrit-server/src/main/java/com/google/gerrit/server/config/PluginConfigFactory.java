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

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class PluginConfigFactory {
  private final Config cfg;
  private final ProjectCache projectCache;

  @Inject
  PluginConfigFactory(@GerritServerConfig Config cfg, ProjectCache projectCache) {
    this.cfg = cfg;
    this.projectCache = projectCache;
  }

  public PluginConfig get(String pluginName) {
    return new PluginConfig(pluginName, cfg);
  }

  public PluginConfig get(Project.NameKey projectName, String pluginName)
      throws NoSuchProjectException {
    ProjectState projectState = projectCache.get(projectName);
    if (projectState == null) {
      throw new NoSuchProjectException(projectName);
    }
    return projectState.getConfig().getPluginConfig(pluginName);
  }
}
