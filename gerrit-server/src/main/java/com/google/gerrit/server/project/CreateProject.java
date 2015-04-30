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

package com.google.gerrit.server.project;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.List;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
public class CreateProject implements RestModifyView<TopLevelResource, ProjectInput> {
  public static interface Factory {
    CreateProject create(String name);
  }

  private final PerformCreateProject.Factory createProjectFactory;
  private final Provider<ProjectsCollection> projectsCollection;
  private final Provider<GroupsCollection> groupsCollection;
  private final DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  private final ProjectJson json;
  private final ProjectControl.GenericFactory projectControlFactory;
  private final Provider<CurrentUser> currentUser;
  private final Provider<PutConfig> putConfig;
  private final String name;

  @Inject
  CreateProject(PerformCreateProject.Factory performCreateProjectFactory,
      Provider<ProjectsCollection> projectsCollection,
      Provider<GroupsCollection> groupsCollection, ProjectJson json,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      ProjectControl.GenericFactory projectControlFactory,
      Provider<CurrentUser> currentUser, Provider<PutConfig> putConfig,
      @Assisted String name) {
    this.createProjectFactory = performCreateProjectFactory;
    this.projectsCollection = projectsCollection;
    this.groupsCollection = groupsCollection;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.json = json;
    this.projectControlFactory = projectControlFactory;
    this.currentUser = currentUser;
    this.putConfig = putConfig;
    this.name = name;
  }

  @Override
  public Response<ProjectInfo> apply(TopLevelResource resource, ProjectInput input)
      throws BadRequestException, UnprocessableEntityException,
      ResourceConflictException, ProjectCreationFailedException,
      ResourceNotFoundException, IOException {
    if (input == null) {
      input = new ProjectInput();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    final CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(name);
    if (!Strings.isNullOrEmpty(input.parent)) {
      args.newParent = projectsCollection.get().parse(input.parent).getControl();
    }
    args.createEmptyCommit = input.createEmptyCommit;
    args.permissionsOnly = input.permissionsOnly;
    args.projectDescription = Strings.emptyToNull(input.description);
    args.submitType = input.submitType;
    args.branch = input.branches;
    if (input.owners != null) {
      List<AccountGroup.UUID> ownerIds =
          Lists.newArrayListWithCapacity(input.owners.size());
      for (String owner : input.owners) {
        ownerIds.add(groupsCollection.get().parse(owner).getGroupUUID());
      }
      args.ownerIds = ownerIds;
    }
    args.contributorAgreements =
        MoreObjects.firstNonNull(input.useContributorAgreements,
            InheritableBoolean.INHERIT);
    args.signedOffBy =
        MoreObjects.firstNonNull(input.useSignedOffBy,
            InheritableBoolean.INHERIT);
    args.contentMerge =
        input.submitType == SubmitType.FAST_FORWARD_ONLY
            ? InheritableBoolean.FALSE : MoreObjects.firstNonNull(
                input.useContentMerge,
                InheritableBoolean.INHERIT);
    args.newChangeForAllNotInTarget =
        MoreObjects.firstNonNull(input.createNewChangeForAllNotInTarget,
            InheritableBoolean.INHERIT);
    args.changeIdRequired =
        MoreObjects.firstNonNull(input.requireChangeId, InheritableBoolean.INHERIT);
    try {
      args.maxObjectSizeLimit =
          ProjectConfig.validMaxObjectSizeLimit(input.maxObjectSizeLimit);
    } catch (ConfigInvalidException e) {
      throw new BadRequestException(e.getMessage());
    }

    for (ProjectCreationValidationListener l : projectCreationValidationListeners) {
      try {
        l.validateNewProject(args);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }

    Project p = createProjectFactory.create(args).createProject();

    if (input.pluginConfigValues != null) {
      try {
        ProjectControl projectControl =
            projectControlFactory.controlFor(p.getNameKey(), currentUser.get());
        PutConfig.Input in = new PutConfig.Input();
        in.pluginConfigValues = input.pluginConfigValues;
        putConfig.get().apply(projectControl, in);
      } catch (NoSuchProjectException e) {
        throw new ResourceNotFoundException(p.getName());
      }
    }

    return Response.created(json.format(p));
  }
}
