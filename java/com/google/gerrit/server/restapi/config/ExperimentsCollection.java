// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ExperimentResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Inject;

public class ExperimentsCollection implements ChildCollection<ConfigResource, ExperimentResource> {
  private final PermissionBackend permissionBackend;
  private final DynamicMap<RestView<ExperimentResource>> views;
  private final ListExperiments list;

  @Inject
  ExperimentsCollection(
      PermissionBackend permissionBackend,
      DynamicMap<RestView<ExperimentResource>> views,
      ListExperiments list) {
    this.permissionBackend = permissionBackend;
    this.views = views;
    this.list = list;
  }

  @Override
  public RestView<ConfigResource> list() throws RestApiException {
    return list;
  }

  @Override
  public ExperimentResource parse(ConfigResource parent, IdString id)
      throws ResourceNotFoundException, Exception {
    permissionBackend.currentUser().check(GlobalPermission.ADMINISTRATE_SERVER);

    if (ListExperiments.getExperiments().stream().noneMatch(id.get()::equalsIgnoreCase)) {
      throw new ResourceNotFoundException(id.get());
    }

    return new ExperimentResource(id.get());
  }

  @Override
  public DynamicMap<RestView<ExperimentResource>> views() {
    return views;
  }
}
