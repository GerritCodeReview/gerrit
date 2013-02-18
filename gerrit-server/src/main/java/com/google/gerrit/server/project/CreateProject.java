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

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.State;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.CreateProject.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
class CreateProject implements RestModifyView<TopLevelResource, Input> {
  static class Input {
    String name;
    String parent;
    String description;
    boolean permissionsOnly;
    boolean createEmptyCommit;
    List<String> branches;
    List<String> ownerIds;
    SubmitType submitType;
    State state;
    InheritableBoolean useContributorAgreements;
    InheritableBoolean useSignedOffBy;
    InheritableBoolean useContentMerge;
    InheritableBoolean requireChangeId;
  }
  static class ProjectInfo {
    final String kind = "gerritcodereview#project";
    String id;
    String name;
    String parent;
    String description;
    boolean permissionsOnly;
    boolean createEmptyCommit;
    List<String> branches;
    List<String> ownerIds;
    SubmitType submitType;
    State state;
    InheritableBoolean useContributorAgreements;
    InheritableBoolean useSignedOffBy;
    InheritableBoolean useContentMerge;
    InheritableBoolean requireChangeId;

    void finish(Input input, final AllProjectsName allProjectName,
        final Set<AccountGroup.UUID> projectOwnerGroups) {

      id = Url.encode(input.name);
      this.name = input.name;
      if (Strings.isNullOrEmpty(input.parent)) {
        this.parent = allProjectName.get();
      }
      this.description = Strings.emptyToNull(input.description);
      this.permissionsOnly = input.permissionsOnly;
      this.createEmptyCommit = input.createEmptyCommit;
      this.branches =
          input.branches == null ? Collections.singletonList(Constants.R_HEADS
              + Constants.MASTER) : input.branches;

      if (input.ownerIds != null && !input.ownerIds.isEmpty()) {
        this.ownerIds = input.ownerIds;
      } else {
        this.ownerIds =
            Lists.newArrayList(Iterables.transform(projectOwnerGroups,
                new Function<AccountGroup.UUID, String>() {
                  @Override
                  public String apply(AccountGroup.UUID owner) {
                    return owner.get();
                  }
                }));
      }

      this.submitType =
          input.submitType == null ? SubmitType.MERGE_IF_NECESSARY
              : input.submitType;
      this.state = input.state == null ? State.ACTIVE : input.state;
      this.useContributorAgreements =
          input.useContributorAgreements == null ? InheritableBoolean.INHERIT
              : input.useContributorAgreements;
      this.useSignedOffBy =
          input.useSignedOffBy == null ? InheritableBoolean.INHERIT
              : input.useSignedOffBy;
      this.requireChangeId =
          input.requireChangeId == null ? InheritableBoolean.INHERIT
              : input.requireChangeId;
      if (input.submitType == SubmitType.FAST_FORWARD_ONLY) {
        this.useContentMerge = InheritableBoolean.FALSE;
      } else {
        this.useContentMerge =
            input.useContentMerge == null ? InheritableBoolean.INHERIT
                : input.useContentMerge;
      }
    }
  }
  static interface Factory {
    CreateProject create(String name);
  }

  private final PerformCreateProject.Factory createProjectFactory;
  private final ProjectControl.GenericFactory controlFactory;
  private final Provider<CurrentUser> user;
  private final String name;
  private final AllProjectsName allProjectName;
  private final Set<AccountGroup.UUID> projectOwnerGroups;
  private final GroupBackend groupBackend;

  @Inject
  CreateProject(ProjectControl.GenericFactory controlFactory,
      Provider<CurrentUser> user, final AllProjectsName allProjectName,
      @ProjectOwnerGroups Set<AccountGroup.UUID> pOwnerGroups,
      PerformCreateProject.Factory performCreateProjectFactory,
      GroupBackend groupBackend, @Assisted String name) {
    this.controlFactory = controlFactory;
    this.createProjectFactory = performCreateProjectFactory;
    this.user = user;
    this.name = name;
    this.allProjectName = allProjectName;
    this.projectOwnerGroups = pOwnerGroups;
    this.groupBackend = groupBackend;
  }

  @Override
  public Object apply(TopLevelResource resource, Input input)
      throws BadRequestException, ProjectCreationFailedException {
    if (input == null) {
      input = new Input();
    }

    if (!name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    final CreateProjectArgs args = new CreateProjectArgs();
    if (!Strings.isNullOrEmpty(input.parent)) {
      ProjectControl ctl;
      try {
        ctl =
            controlFactory.controlFor(new Project.NameKey(input.parent),
                user.get());
        args.newParent = ctl;
      } catch (NoSuchProjectException e) {
        throw new BadRequestException("Parent project \"" + input.parent
            + "\" does not exist or is not visible'.");
      }
      if (!ctl.isVisible() && !ctl.isOwner()) {
        throw new BadRequestException("Parent project \"" + input.parent
            + "\" does not exist or is not visible'.");
      }
    }

    args.setProjectName(input.name);
    args.createEmptyCommit = input.createEmptyCommit;
    args.permissionsOnly = input.permissionsOnly;
    args.projectDescription = Strings.emptyToNull(input.description);
    args.branch = input.branches;

    if (input.ownerIds != null && !input.ownerIds.isEmpty()) {
      args.ownerIds =
          Lists.newArrayList(Iterables.transform(input.ownerIds,
              new Function<String, AccountGroup.UUID>() {
                @Override
                public AccountGroup.UUID apply(String owner) {
                  return new AccountGroup.UUID(owner);
                }
              }));

      for (AccountGroup.UUID ownerId : args.ownerIds) {
        GroupDescription.Basic g = groupBackend.get(ownerId);
        if (g == null) {
          throw new BadRequestException(String.format(
              "owner id: '%s' is wrong.", ownerId.get()));
        }
      }
    }
    if (input.state != null) {
      args.state = input.state;
    }
    if (input.submitType != null) {
      args.submitType = input.submitType;
    }
    if (input.useContributorAgreements != null) {
      args.contributorAgreements = input.useContributorAgreements;
    }
    if (input.useSignedOffBy != null) {
      args.signedOffBy = input.useSignedOffBy;
    }
    if (input.useContentMerge != null) {
      args.contentMerge = input.useContentMerge;
    }
    if (input.requireChangeId != null) {
      args.changeIdRequired = input.requireChangeId;
    }
    createProjectFactory.create(args).createProject();

    ProjectInfo info = new ProjectInfo();
    info.finish(input, allProjectName, projectOwnerGroups);
    return Response.created(info);
  }
}
