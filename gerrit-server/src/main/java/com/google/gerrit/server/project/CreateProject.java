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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.CreateProject.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.List;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
class CreateProject implements RestModifyView<TopLevelResource, Input> {
  static class Input {
    String name;
    String parent;
    String description;
    boolean permissionsOnly;
    boolean createEmptyCommit;
    SubmitType submitType;
    List<String> branches;
    List<AccountGroup.UUID> ownerIds;
    InheritableBoolean useContributorAgreements;
    InheritableBoolean useSignedOffBy;
    InheritableBoolean useContentMerge;
    InheritableBoolean requireChangeId ;
  }
  static class ProjectInfo {
    final String kind = "gerritcodereview#project";
    String id;
    Input createdWith;

    void finish(Input input, final AllProjectsName allProjectName) {
      createdWith = input;
      id = Url.encode(createdWith.name);
      if (Strings.isNullOrEmpty(createdWith.parent)) {
        createdWith.parent = allProjectName.get();
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

  @Inject
  CreateProject(ProjectControl.GenericFactory controlFactory,
      Provider<CurrentUser> user, final AllProjectsName allProjectName,
      PerformCreateProject.Factory performCreateProjectFactory,
      @Assisted String name) {
    this.controlFactory = controlFactory;
    this.createProjectFactory = performCreateProjectFactory;
    this.user = user;
    this.name = name;
    this.allProjectName = allProjectName;
  }

  @Override
  public Object apply(TopLevelResource resource, Input input)
      throws AuthException, BadRequestException, OrmException,
      NameAlreadyUsedException, ProjectCreationFailedException {
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
    args.submitType =
        input.submitType == null ? SubmitType.MERGE_IF_NECESSARY
            : input.submitType;
    args.branch = input.branches;
    args.ownerIds = input.ownerIds  ;

    args.contributorAgreements =
        input.useContributorAgreements == null ? InheritableBoolean.INHERIT
            : input.useContributorAgreements;
    args.signedOffBy =
        input.useSignedOffBy == null ? InheritableBoolean.INHERIT
            : input.useSignedOffBy;
    args.contentMerge =
        input.useContentMerge == null ? InheritableBoolean.INHERIT
            : input.useContentMerge;
    args.changeIdRequired =
        input.requireChangeId == null ? InheritableBoolean.INHERIT
            : input.requireChangeId;

    createProjectFactory.create(args).createProject();
    ProjectInfo info = new ProjectInfo();
    info.finish(input, allProjectName);
    return Response.created(info);
  }
}
