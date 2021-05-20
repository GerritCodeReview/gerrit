// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestCollectionCreateView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Optional;

@Singleton
public class CreateSubmitRequirement
    implements RestCollectionCreateView<
        ProjectResource, SubmitRequirementResource, SubmitRequirementInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;

  @Inject
  public CreateSubmitRequirement(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
  }

  @Override
  public Response<SubmitRequirementInfo> apply(
      ProjectResource rsrc, IdString id, SubmitRequirementInput input) throws Exception {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new SubmitRequirementInput();
    }

    if (input.name != null && !input.name.equals(id.get())) {
      throw new BadRequestException("name in input must match name in URL");
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      SubmitRequirement submitRequirement = createSubmitRequirement(config, id.get(), input);

      md.setMessage("Create Submit Requirement");
      config.commit(md);

      projectCache.evict(rsrc.getProjectState().getProject());

      return Response.created(SubmitRequirementJson.format(submitRequirement));
    }
  }

  public SubmitRequirement createSubmitRequirement(
      ProjectConfig config, String name, SubmitRequirementInput input)
      throws BadRequestException, ResourceConflictException {
    if (config.getSubmitRequirementSections().containsKey(name)) {
      throw new ResourceConflictException(
          String.format("submit requirement %s already exists", name));
    }

    for (String srName : config.getSubmitRequirementSections().keySet()) {
      if (srName.equalsIgnoreCase(name)) {
        throw new ResourceConflictException(
            String.format(
                "submit requirement \"%s\" conflicts with existing submit requirement \"%s\"",
                name, srName));
      }
    }

    if (Strings.isNullOrEmpty(input.submittabilityExpression)) {
      throw new BadRequestException("submittability_expression is required");
    }

    SubmitRequirement submitRequirement =
        SubmitRequirement.builder()
            .setName(name)
            .setDescription(Optional.ofNullable(input.description))
            .setApplicabilityExpression(
                SubmitRequirementExpression.of(input.applicabilityExpression))
            .setBlockingExpression(
                SubmitRequirementExpression.create(input.submittabilityExpression))
            .setOverrideExpression(SubmitRequirementExpression.of(input.overrideExpression))
            .setAllowOverrideInChildProjects(input.allowOverrideInChildProjects)
            .build();

    config.upsertSubmitRequirement(submitRequirement);
    return submitRequirement;
  }
}
