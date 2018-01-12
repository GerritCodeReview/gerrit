// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.api.projects.CheckProjectInput;
import com.google.gerrit.extensions.api.projects.CheckProjectResultInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectsConsistencyChecker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class Check implements RestModifyView<ProjectResource, CheckProjectInput> {
  private final PermissionBackend permissionBackend;
  private final ProjectsConsistencyChecker projectsConsistencyChecker;

  @Inject
  Check(
      PermissionBackend permissionBackend, ProjectsConsistencyChecker projectsConsistencyChecker) {
    this.permissionBackend = permissionBackend;
    this.projectsConsistencyChecker = projectsConsistencyChecker;
  }

  @Override
  public CheckProjectResultInfo apply(ProjectResource rsrc, CheckProjectInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    permissionBackend.user(rsrc.getUser()).check(GlobalPermission.ADMINISTRATE_SERVER);
    return projectsConsistencyChecker.check(rsrc.getNameKey(), input);
  }
}
