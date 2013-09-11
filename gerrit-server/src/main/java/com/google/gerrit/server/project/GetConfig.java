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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.config.DownloadCommand;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GetConfig implements RestReadView<ProjectResource> {

  private final TransferConfig config;
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<CurrentUser> currentUser;
  private final DynamicSet<DownloadScheme> downloadSchemes;
  private final DynamicSet<DownloadCommand> downloadCommands;

  @Inject
  public GetConfig(TransferConfig config,
      DynamicMap<RestView<ProjectResource>> views,
      Provider<CurrentUser> currentUser,
      DynamicSet<DownloadScheme> downloadSchemes,
      DynamicSet<DownloadCommand> downloadCommands) {
    this.config = config;
    this.views = views;
    this.currentUser = currentUser;
    this.downloadSchemes = downloadSchemes;
    this.downloadCommands = downloadCommands;
  }

  @Override
  public ConfigInfo apply(ProjectResource resource) {
    return new ConfigInfo(resource.getControl(),
        resource.getControl().getProjectState(),
        config,
        views,
        currentUser,
        downloadSchemes,
        downloadCommands);
  }
}
