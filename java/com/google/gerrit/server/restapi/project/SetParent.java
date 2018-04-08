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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.api.projects.ParentInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class SetParent implements RestModifyView<ProjectResource, ParentInput> {
  private final ProjectCache cache;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.Server updateFactory;
  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;

  @Inject
  SetParent(
      ProjectCache cache,
      PermissionBackend permissionBackend,
      MetaDataUpdate.Server updateFactory,
      AllProjectsName allProjects,
      AllUsersName allUsers) {
    this.cache = cache;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.allProjects = allProjects;
    this.allUsers = allUsers;
  }

  @Override
  public String apply(ProjectResource rsrc, ParentInput input)
      throws AuthException, ResourceConflictException, ResourceNotFoundException,
          UnprocessableEntityException, IOException, PermissionBackendException,
          BadRequestException {
    return apply(rsrc, input, true);
  }

  public String apply(ProjectResource rsrc, ParentInput input, boolean checkIfAdmin)
      throws AuthException, ResourceConflictException, ResourceNotFoundException,
          UnprocessableEntityException, IOException, PermissionBackendException,
          BadRequestException {
    IdentifiedUser user = rsrc.getUser().asIdentifiedUser();
    String parentName =
        MoreObjects.firstNonNull(Strings.emptyToNull(input.parent), allProjects.get());
    validateParentUpdate(rsrc.getProjectState().getNameKey(), user, parentName, checkIfAdmin);
    try (MetaDataUpdate md = updateFactory.create(rsrc.getNameKey())) {
      ProjectConfig config = ProjectConfig.read(md);
      Project project = config.getProject();
      project.setParentName(parentName);

      String msg = Strings.emptyToNull(input.commitMessage);
      if (msg == null) {
        msg = String.format("Changed parent to %s.\n", parentName);
      } else if (!msg.endsWith("\n")) {
        msg += "\n";
      }
      md.setAuthor(user);
      md.setMessage(msg);
      config.commit(md);
      cache.evict(rsrc.getProjectState().getProject());

      Project.NameKey parent = project.getParent(allProjects);
      checkNotNull(parent);
      return parent.get();
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(rsrc.getName());
    } catch (ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("invalid project.config: %s", e.getMessage()));
    }
  }

  public void validateParentUpdate(
      Project.NameKey project, IdentifiedUser user, String newParent, boolean checkIfAdmin)
      throws AuthException, ResourceConflictException, UnprocessableEntityException,
          PermissionBackendException, BadRequestException {
    if (checkIfAdmin) {
      permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
    }

    if (project.equals(allUsers) && !allProjects.get().equals(newParent)) {
      throw new BadRequestException(
          String.format("%s must inherit from %s", allUsers.get(), allProjects.get()));
    }

    if (project.equals(allProjects)) {
      throw new ResourceConflictException("cannot set parent of " + allProjects.get());
    }

    if (allUsers.get().equals(newParent)) {
      throw new ResourceConflictException(
          String.format("Cannot inherit from '%s' project", allUsers.get()));
    }

    newParent = Strings.emptyToNull(newParent);
    if (newParent != null) {
      ProjectState parent = cache.get(new Project.NameKey(newParent));
      if (parent == null) {
        throw new UnprocessableEntityException("parent project " + newParent + " not found");
      }

      if (parent.getName().equals(project.get())) {
        throw new ResourceConflictException("cannot set parent to self");
      }

      if (Iterables.tryFind(
              parent.tree(),
              p -> {
                return p.getNameKey().equals(project);
              })
          .isPresent()) {
        throw new ResourceConflictException(
            "cycle exists between " + project.get() + " and " + parent.getName());
      }
    }
  }
}
