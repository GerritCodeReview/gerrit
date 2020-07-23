// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.entities.LabelType;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.LabelResource;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class LabelsCollection implements ChildCollection<ProjectResource, LabelResource> {
  private final Provider<ListLabels> list;
  private final DynamicMap<RestView<LabelResource>> views;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;

  @Inject
  LabelsCollection(
      Provider<ListLabels> list,
      DynamicMap<RestView<LabelResource>> views,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend) {
    this.list = list;
    this.views = views;
    this.user = user;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public RestView<ProjectResource> list() throws RestApiException {
    return list.get();
  }

  @Override
  public LabelResource parse(ProjectResource parent, IdString id)
      throws AuthException, ResourceNotFoundException, PermissionBackendException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(parent.getNameKey())
        .check(ProjectPermission.READ_CONFIG);
    LabelType labelType = parent.getProjectState().getConfig().getLabelSections().get(id.get());
    if (labelType == null) {
      throw new ResourceNotFoundException(id);
    }
    return new LabelResource(parent, labelType);
  }

  @Override
  public DynamicMap<RestView<LabelResource>> views() {
    return views;
  }
}
