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

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.ProjectUtil;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ProjectOwnerGroupsProvider;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectCreator;
import com.google.gerrit.server.project.ProjectJson;
import com.google.gerrit.server.project.ProjectNameLockManager;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
@Singleton
public class CreateProject
    implements RestCollectionCreateView<TopLevelResource, ProjectResource, ProjectInput> {
  private final Provider<ProjectsCollection> projectsCollection;
  private final Provider<GroupResolver> groupResolver;
  private final PluginSetContext<ProjectCreationValidationListener>
      projectCreationValidationListeners;
  private final ProjectJson json;
  private final ProjectOwnerGroupsProvider.Factory projectOwnerGroups;
  private final Provider<PutConfig> putConfig;
  private final AllProjectsName allProjects;
  private final AllUsersName allUsers;
  private final PluginItemContext<ProjectNameLockManager> lockManager;
  private final ProjectCreator projectCreator;

  private final Config gerritConfig;

  @Inject
  CreateProject(
      ProjectCreator projectCreator,
      Provider<ProjectsCollection> projectsCollection,
      Provider<GroupResolver> groupResolver,
      ProjectJson json,
      PluginSetContext<ProjectCreationValidationListener> projectCreationValidationListeners,
      ProjectOwnerGroupsProvider.Factory projectOwnerGroups,
      Provider<PutConfig> putConfig,
      AllProjectsName allProjects,
      AllUsersName allUsers,
      PluginItemContext<ProjectNameLockManager> lockManager,
      @GerritServerConfig Config gerritConfig) {
    this.projectsCollection = projectsCollection;
    this.projectCreator = projectCreator;
    this.groupResolver = groupResolver;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.json = json;
    this.projectOwnerGroups = projectOwnerGroups;
    this.putConfig = putConfig;
    this.allProjects = allProjects;
    this.allUsers = allUsers;
    this.lockManager = lockManager;
    this.gerritConfig = gerritConfig;
  }

  @Override
  public Response<ProjectInfo> apply(TopLevelResource resource, IdString id, ProjectInput input)
      throws RestApiException, IOException, ConfigInvalidException, PermissionBackendException {
    String name = id.get();
    if (input == null) {
      input = new ProjectInput();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(ProjectUtil.sanitizeProjectName(name));

    String parentName =
        MoreObjects.firstNonNull(Strings.emptyToNull(input.parent), allProjects.get());
    args.newParent = projectsCollection.get().parse(parentName, false).getNameKey();
    if (args.newParent.equals(allUsers)) {
      throw new ResourceConflictException(
          String.format("Cannot inherit from '%s' project", allUsers.get()));
    }
    args.createEmptyCommit = input.createEmptyCommit;
    args.permissionsOnly = input.permissionsOnly;
    args.projectDescription = Strings.emptyToNull(input.description);
    args.submitType = input.submitType;
    args.branch = normalizeBranchNames(input.branches);
    if (input.owners == null || input.owners.isEmpty()) {
      args.ownerIds = new ArrayList<>(projectOwnerGroups.create(args.getProject()).get());
    } else {
      args.ownerIds = Lists.newArrayListWithCapacity(input.owners.size());
      for (String owner : input.owners) {
        args.ownerIds.add(groupResolver.get().parse(owner).getGroupUUID());
      }
    }
    args.contributorAgreements =
        MoreObjects.firstNonNull(input.useContributorAgreements, InheritableBoolean.INHERIT);
    args.signedOffBy = MoreObjects.firstNonNull(input.useSignedOffBy, InheritableBoolean.INHERIT);
    args.contentMerge =
        input.submitType == SubmitType.FAST_FORWARD_ONLY
            ? InheritableBoolean.FALSE
            : MoreObjects.firstNonNull(input.useContentMerge, InheritableBoolean.INHERIT);
    args.newChangeForAllNotInTarget =
        MoreObjects.firstNonNull(
            input.createNewChangeForAllNotInTarget, InheritableBoolean.INHERIT);
    args.changeIdRequired =
        MoreObjects.firstNonNull(input.requireChangeId, InheritableBoolean.INHERIT);
    args.rejectEmptyCommit =
        MoreObjects.firstNonNull(input.rejectEmptyCommit, InheritableBoolean.INHERIT);
    args.enableSignedPush =
        MoreObjects.firstNonNull(input.enableSignedPush, InheritableBoolean.INHERIT);
    args.requireSignedPush =
        MoreObjects.firstNonNull(input.requireSignedPush, InheritableBoolean.INHERIT);
    try {
      args.maxObjectSizeLimit = ProjectConfig.validMaxObjectSizeLimit(input.maxObjectSizeLimit);
    } catch (ConfigInvalidException e) {
      throw new BadRequestException(e.getMessage());
    }

    Lock nameLock = lockManager.call(lockManager -> lockManager.getLock(args.getProject()));
    nameLock.lock();
    try {
      try {
        projectCreationValidationListeners.runEach(
            l -> l.validateNewProject(args), ValidationException.class);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }

      ProjectState projectState = projectCreator.createProject(args);
      requireNonNull(
          projectState,
          () -> String.format("failed to create project %s", args.getProject().get()));

      if (input.pluginConfigValues != null) {
        ConfigInput in = new ConfigInput();
        in.pluginConfigValues = input.pluginConfigValues;
        in.description = args.projectDescription;
        putConfig.get().apply(projectState, in);
      }
      return Response.created(json.format(projectState));
    } finally {
      nameLock.unlock();
    }
  }

  private ImmutableList<String> normalizeBranchNames(List<String> branches)
      throws BadRequestException {
    if (branches == null || branches.isEmpty()) {
      // Use host-level default for HEAD or fall back to 'master' if nothing else was specified in
      // the input.
      String defaultBranch = gerritConfig.getString("gerrit", null, "defaultBranch");
      defaultBranch =
          defaultBranch != null
              ? normalizeAndValidateBranch(defaultBranch)
              : Constants.R_HEADS + Constants.MASTER;
      return ImmutableList.of(defaultBranch);
    }
    List<String> normalizedBranches = new ArrayList<>();
    for (String branch : branches) {
      branch = normalizeAndValidateBranch(branch);
      if (!normalizedBranches.contains(branch)) {
        normalizedBranches.add(branch);
      }
    }
    return ImmutableList.copyOf(normalizedBranches);
  }

  private String normalizeAndValidateBranch(String branch) throws BadRequestException {
    while (branch.startsWith("/")) {
      branch = branch.substring(1);
    }
    branch = RefNames.fullName(branch);
    if (!Repository.isValidRefName(branch)) {
      throw new BadRequestException(String.format("Branch \"%s\" is not a valid name.", branch));
    }
    return branch;
  }

  static class ValidBranchListener implements ProjectCreationValidationListener {
    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      for (String branch : args.branch) {
        if (RefNames.isGerritRef(branch)) {
          throw new ValidationException(
              String.format(
                  "Cannot create a project with branch %s. Branches in the Gerrit internal refs namespace are not allowed",
                  branch));
        }
      }
    }
  }
}
