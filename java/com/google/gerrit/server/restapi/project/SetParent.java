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

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.projects.ParentInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.ConfigKey;
import com.google.gerrit.server.config.ConfigUpdatedEvent;
import com.google.gerrit.server.config.ConfigUpdatedEvent.ConfigUpdateEntry;
import com.google.gerrit.server.config.ConfigUpdatedEvent.UpdateResult;
import com.google.gerrit.server.config.GerritConfigListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;

@Singleton
public class SetParent
    implements RestModifyView<ProjectResource, ParentInput>, GerritConfigListener {
  private final ProjectCache cache;
  private final PermissionBackend permissionBackend;
  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;
  private final RepoMetaDataUpdater repoMetaDataUpdater;
  private volatile boolean allowProjectOwnersToChangeParent;

  @Inject
  SetParent(
      ProjectCache cache,
      PermissionBackend permissionBackend,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      @GerritServerConfig Config config,
      RepoMetaDataUpdater repoMetaDataUpdater) {
    this.cache = cache;
    this.permissionBackend = permissionBackend;
    this.allProjects = allProjects;
    this.allUsers = allUsers;
    this.allowProjectOwnersToChangeParent =
        config.getBoolean("receive", "allowProjectOwnersToChangeParent", false);
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<String> apply(ProjectResource rsrc, ParentInput input)
      throws AuthException, ResourceConflictException, ResourceNotFoundException,
          UnprocessableEntityException, IOException, PermissionBackendException,
          BadRequestException, MethodNotAllowedException {
    return Response.ok(apply(rsrc, input, true));
  }

  public String apply(ProjectResource rsrc, ParentInput input, boolean checkIfAdmin)
      throws AuthException, ResourceConflictException, ResourceNotFoundException,
          UnprocessableEntityException, IOException, PermissionBackendException,
          BadRequestException, MethodNotAllowedException {
    IdentifiedUser user = rsrc.getUser().asIdentifiedUser();
    String parentName =
        MoreObjects.firstNonNull(Strings.emptyToNull(input.parent), allProjects.get());
    validateParentUpdate(rsrc.getProjectState().getNameKey(), user, parentName, checkIfAdmin);
    try (var configUpdater =
        repoMetaDataUpdater.configUpdaterWithoutPermissionsCheck(
            rsrc.getNameKey(),
            input.commitMessage,
            String.format("Changed parent to %s.\n", parentName))) {
      ProjectConfig config = configUpdater.getConfig();
      config.updateProject(p -> p.setParent(parentName));
      configUpdater.commitConfigUpdate();

      Project.NameKey parent = config.getProject().getParent(allProjects);
      requireNonNull(parent);
      return parent.get();
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(rsrc.getName(), notFound);
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
      if (allowProjectOwnersToChangeParent) {
        permissionBackend.user(user).project(project).check(ProjectPermission.WRITE_CONFIG);
      } else {
        permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
      }
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
      Project.NameKey newParentNameKey = Project.nameKey(newParent);
      ProjectState parent =
          cache
              .get(newParentNameKey)
              .orElseThrow(
                  () ->
                      new UnprocessableEntityException(
                          "parent project " + newParentNameKey + " not found"));

      if (parent.getName().equals(project.get())) {
        throw new ResourceConflictException("cannot set parent to self");
      }

      if (Iterables.tryFind(parent.tree(), p -> p.getNameKey().equals(project)).isPresent()) {
        throw new ResourceConflictException(
            "cycle exists between " + project.get() + " and " + parent.getName());
      }
    }
  }

  @Override
  public Multimap<UpdateResult, ConfigUpdateEntry> configUpdated(ConfigUpdatedEvent event) {
    ConfigKey receiveSetParent = ConfigKey.create("receive", "allowProjectOwnersToChangeParent");
    if (!event.isValueUpdated(receiveSetParent)) {
      return ConfigUpdatedEvent.NO_UPDATES;
    }
    try {
      boolean enabled =
          event.getNewConfig().getBoolean("receive", "allowProjectOwnersToChangeParent", false);
      this.allowProjectOwnersToChangeParent = enabled;
    } catch (IllegalArgumentException iae) {
      return event.reject(receiveSetParent);
    }
    return event.accept(receiveSetParent);
  }
}
