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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.project.CreateProject.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
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
    List<String> owners;
    InheritableBoolean useContributorAgreements;
    InheritableBoolean useSignedOffBy;
    InheritableBoolean useContentMerge;
    InheritableBoolean requireChangeId;
  }

  static interface Factory {
    CreateProject create(String name);
  }

  private final PerformCreateProject.Factory createProjectFactory;
  private final Provider<ProjectsCollection> projectsCollection;
  private final Provider<GroupsCollection> groupsCollection;
  private final ProjectJson json;
  private final String name;

  @Inject
  CreateProject(PerformCreateProject.Factory performCreateProjectFactory,
      Provider<ProjectsCollection> projectsCollection,
      Provider<GroupsCollection> groupsCollection, ProjectJson json,
      @Assisted String name) {
    this.createProjectFactory = performCreateProjectFactory;
    this.projectsCollection = projectsCollection;
    this.groupsCollection = groupsCollection;
    this.json = json;
    this.name = name;
  }

  @Override
  public Object apply(TopLevelResource resource, Input input)
      throws BadRequestException, UnprocessableEntityException,
      ProjectCreationFailedException, IOException {
    if (input == null) {
      input = new Input();
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
    args.submitType =
        Objects.firstNonNull(input.submitType, SubmitType.MERGE_IF_NECESSARY);
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
        Objects.firstNonNull(input.useContributorAgreements,
            InheritableBoolean.INHERIT);
    args.signedOffBy =
        Objects.firstNonNull(input.useSignedOffBy, InheritableBoolean.INHERIT);
    args.contentMerge =
        input.submitType == SubmitType.FAST_FORWARD_ONLY
            ? InheritableBoolean.FALSE : Objects.firstNonNull(
                input.useContentMerge, InheritableBoolean.INHERIT);
    args.changeIdRequired =
        Objects.firstNonNull(input.requireChangeId, InheritableBoolean.INHERIT);

    Project p = createProjectFactory.create(args).createProject();
    return Response.created(json.format(p));
  }
}
