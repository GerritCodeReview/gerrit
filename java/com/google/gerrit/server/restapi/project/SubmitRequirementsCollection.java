// Copyright (C) 2022 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class SubmitRequirementsCollection
    implements ChildCollection<ProjectResource, SubmitRequirementResource> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final DynamicMap<RestView<SubmitRequirementResource>> views;

  @Inject
  SubmitRequirementsCollection(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      DynamicMap<RestView<SubmitRequirementResource>> views) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.views = views;
  }

  @Override
  public RestView<ProjectResource> list() throws RestApiException {
    /** TODO(ghareeb): implement. */
    throw new NotImplementedException();
  }

  @Override
  public SubmitRequirementResource parse(ProjectResource parent, IdString id)
      throws AuthException, ResourceNotFoundException, PermissionBackendException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(parent.getNameKey())
        .check(ProjectPermission.READ_CONFIG);

    SubmitRequirement submitRequirement =
        parent.getProjectState().getConfig().getSubmitRequirementSections().get(id.get());

    if (submitRequirement == null) {
      throw new ResourceNotFoundException(id);
    }
    return new SubmitRequirementResource(parent, submitRequirement);
  }

  @Override
  public DynamicMap<RestView<SubmitRequirementResource>> views() {
    return views;
  }
}
