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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
@Singleton
public class PutProject implements RestModifyView<ProjectResource, ProjectInput> {
  private final CreateProject createProject;

  @Inject
  PutProject(CreateProject createProject) {
    this.createProject = createProject;
  }

  @Override
  public Response<?> apply(ProjectResource projectResource, ProjectInput input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    if (input.initOnly) {
      return createProject.apply(
          TopLevelResource.INSTANCE, IdString.fromDecoded(input.name), input);
    }

    throw new ResourceConflictException(
        "Project \"" + projectResource.getName() + "\" already exists");
  }
}
