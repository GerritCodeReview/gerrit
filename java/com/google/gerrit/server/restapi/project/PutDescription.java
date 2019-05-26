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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.DescriptionInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class PutDescription implements RestModifyView<ProjectResource, DescriptionInput> {
  private final ProjectCache cache;
  private final MetaDataUpdate.Server updateFactory;
  private final PermissionBackend permissionBackend;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  PutDescription(
      ProjectCache cache,
      MetaDataUpdate.Server updateFactory,
      PermissionBackend permissionBackend,
      ProjectConfig.Factory projectConfigFactory) {
    this.cache = cache;
    this.updateFactory = updateFactory;
    this.permissionBackend = permissionBackend;
    this.projectConfigFactory = projectConfigFactory;
  }

  @Override
  public Response<String> apply(ProjectResource resource, DescriptionInput input)
      throws AuthException, ResourceConflictException, ResourceNotFoundException, IOException,
          PermissionBackendException {
    if (input == null) {
      input = new DescriptionInput(); // Delete would set description to null.
    }

    IdentifiedUser user = resource.getUser().asIdentifiedUser();
    permissionBackend
        .user(user)
        .project(resource.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    try (MetaDataUpdate md = updateFactory.create(resource.getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);
      Project project = config.getProject();
      project.setDescription(Strings.emptyToNull(input.description));

      String msg =
          MoreObjects.firstNonNull(
              Strings.emptyToNull(input.commitMessage), "Updated description.\n");
      if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      md.setAuthor(user);
      md.setMessage(msg);
      config.commit(md);
      cache.evict(resource.getProjectState().getProject());
      md.getRepository().setGitwebDescription(project.getDescription());

      return Strings.isNullOrEmpty(project.getDescription())
          ? Response.none()
          : Response.ok(project.getDescription());
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(resource.getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("invalid project.config: %s", e.getMessage()));
    }
  }
}
