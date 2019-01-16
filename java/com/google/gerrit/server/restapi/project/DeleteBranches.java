// Copyright (C) 2015 The Android Open Source Project
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

import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.projects.DeleteBranchesInput;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class DeleteBranches implements RestModifyView<ProjectResource, DeleteBranchesInput> {
  private final DeleteRef deleteRef;

  @Inject
  DeleteBranches(DeleteRef deleteRef) {
    this.deleteRef = deleteRef;
  }

  @Override
  public Response<?> apply(ProjectResource project, DeleteBranchesInput input)
      throws StorageException, IOException, RestApiException, PermissionBackendException {
    if (input == null || input.branches == null || input.branches.isEmpty()) {
      throw new BadRequestException("branches must be specified");
    }
    deleteRef.deleteMultipleRefs(
        project.getProjectState(), ImmutableSet.copyOf(input.branches), R_HEADS);
    return Response.none();
  }
}
