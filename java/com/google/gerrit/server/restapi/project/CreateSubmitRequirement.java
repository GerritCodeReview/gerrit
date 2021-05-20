// Copyright (C) 2022 The Android Open Source Project
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
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.SubmitRequirementExpressionsValidator;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.gerrit.server.project.SubmitRequirementsUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class CreateSubmitRequirement
    implements RestCollectionCreateView<
        ProjectResource, SubmitRequirementResource, SubmitRequirementInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final SubmitRequirementExpressionsValidator submitRequirementExpressionsValidator;

  @Inject
  public CreateSubmitRequirement(
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      MetaDataUpdate.User updateFactory,
      ProjectConfig.Factory projectConfigFactory,
      ProjectCache projectCache,
      SubmitRequirementExpressionsValidator submitRequirementExpressionsValidator) {
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.updateFactory = updateFactory;
    this.projectConfigFactory = projectConfigFactory;
    this.projectCache = projectCache;
    this.submitRequirementExpressionsValidator = submitRequirementExpressionsValidator;
  }

  @Override
  public Response<SubmitRequirementInfo> apply(
      ProjectResource rsrc, IdString id, SubmitRequirementInput input)
      throws AuthException, BadRequestException, IOException, PermissionBackendException {
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

      projectCache.evict(rsrc.getProjectState().getProject().getNameKey());

      return Response.created(SubmitRequirementJson.format(submitRequirement));
    } catch (ConfigInvalidException e) {
      throw new IOException("Failed to read project config", e);
    } catch (ResourceConflictException e) {
      throw new BadRequestException("Failed to create submit requirement", e);
    }
  }

  public SubmitRequirement createSubmitRequirement(
      ProjectConfig config, String name, SubmitRequirementInput input)
      throws BadRequestException, ResourceConflictException {
    validateSRName(name, config);
    if (Strings.isNullOrEmpty(input.submittabilityExpression)) {
      throw new BadRequestException("submittability_expression is required");
    }
    if (input.allowOverrideInChildProjects == null) {
      input.allowOverrideInChildProjects = false;
    }
    SubmitRequirement submitRequirement =
        SubmitRequirement.builder()
            .setName(name)
            .setDescription(Optional.ofNullable(input.description))
            .setApplicabilityExpression(
                SubmitRequirementExpression.of(input.applicabilityExpression))
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create(input.submittabilityExpression))
            .setOverrideExpression(SubmitRequirementExpression.of(input.overrideExpression))
            .setAllowOverrideInChildProjects(input.allowOverrideInChildProjects)
            .build();

    List<String> validationMessages =
        submitRequirementExpressionsValidator.validateExpressions(submitRequirement);
    if (!validationMessages.isEmpty()) {
      throw new BadRequestException(
          String.format("Invalid submit requirement input: %s", validationMessages));
    }

    config.upsertSubmitRequirement(submitRequirement);
    return submitRequirement;
  }

  private void validateSRName(String name, ProjectConfig config)
      throws BadRequestException, ResourceConflictException {
    try {
      SubmitRequirementsUtil.validateName(name);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
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
  }
}
