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
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DisablePlugin implements RestModifyView<PluginResource, Input> {

  private final PluginLoader loader;
  private final PermissionBackend permissionBackend;

  @Inject
  DisablePlugin(PluginLoader loader, PermissionBackend permissionBackend) {
    this.loader = loader;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public PluginInfo apply(PluginResource resource, Input input) throws RestApiException {
    try {
      permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);
    } catch (PermissionBackendException e) {
      throw new RestApiException("Could not check permission", e);
    }
    loader.checkRemoteAdminEnabled();
    String name = resource.getName();
    loader.disablePlugins(ImmutableSet.of(name));
    return ListPlugins.toPluginInfo(loader.get(name));
  }
}
