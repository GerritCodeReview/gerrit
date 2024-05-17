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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class PutDescription implements RestModifyView<ProjectResource, DescriptionInput> {
  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  PutDescription(RepoMetaDataUpdater repoMetaDataUpdater) {
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<String> apply(ProjectResource resource, DescriptionInput input)
      throws AuthException, ResourceConflictException, ResourceNotFoundException, IOException,
          PermissionBackendException, BadRequestException, MethodNotAllowedException {
    if (input == null) {
      input = new DescriptionInput(); // Delete would set description to null.
    }

    try (var configUpdater =
        repoMetaDataUpdater.configUpdater(
            resource.getNameKey(), input.commitMessage, "Update description")) {
      ProjectConfig config = configUpdater.getConfig();
      String desc = input.description;
      config.updateProject(p -> p.setDescription(Strings.emptyToNull(desc)));

      configUpdater.commitConfigUpdate();
      configUpdater.getRepository().setGitwebDescription(config.getProject().getDescription());

      return Strings.isNullOrEmpty(config.getProject().getDescription())
          ? Response.none()
          : Response.ok(config.getProject().getDescription());
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(resource.getName(), notFound);
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("invalid project.config: %s", e.getMessage()));
    }
  }
}
