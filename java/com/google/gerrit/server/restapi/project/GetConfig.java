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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class GetConfig implements RestReadView<ProjectResource> {
  private final boolean serverEnableSignedPush;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final PluginConfigFactory cfgFactory;
  private final AllProjectsName allProjects;
  private final PermissionBackend permissionBackend;
  private final UiActions uiActions;
  private final DynamicMap<RestView<ProjectResource>> views;

  @Inject
  public GetConfig(
      @EnableSignedPush boolean serverEnableSignedPush,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsName allProjects,
      PermissionBackend permissionBackend,
      UiActions uiActions,
      DynamicMap<RestView<ProjectResource>> views) {
    this.serverEnableSignedPush = serverEnableSignedPush;
    this.pluginConfigEntries = pluginConfigEntries;
    this.allProjects = allProjects;
    this.cfgFactory = cfgFactory;
    this.permissionBackend = permissionBackend;
    this.uiActions = uiActions;
    this.views = views;
  }

  @Override
  public Response<ConfigInfo> apply(ProjectResource resource) throws PermissionBackendException {
    boolean readConfigAllowed =
        permissionBackend
            .currentUser()
            .project(resource.getNameKey())
            .test(ProjectPermission.READ_CONFIG);
    return Response.ok(
        new ConfigInfoImpl(
            serverEnableSignedPush,
            resource.getProjectState(),
            resource.getUser(),
            readConfigAllowed ? pluginConfigEntries : DynamicMap.emptyMap(),
            cfgFactory,
            allProjects,
            uiActions,
            views));
  }
}
