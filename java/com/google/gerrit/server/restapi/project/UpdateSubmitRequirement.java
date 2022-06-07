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
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
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

/**
 * A rest modify view that updates the definition of an existing submit requirement for a project.
 */
@Singleton
public class UpdateSubmitRequirement
    implements RestModifyView<SubmitRequirementResource, SubmitRequirementInput> {
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final MetaDataUpdate.User updateFactory;
  private final ProjectConfig.Factory projectConfigFactory;
  private final ProjectCache projectCache;
  private final SubmitRequirementExpressionsValidator submitRequirementExpressionsValidator;

  @Inject
  public UpdateSubmitRequirement(
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
      SubmitRequirementResource rsrc, SubmitRequirementInput input)
      throws AuthException, BadRequestException, PermissionBackendException, IOException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    permissionBackend
        .currentUser()
        .project(rsrc.getProject().getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);

    if (input == null) {
      input = new SubmitRequirementInput();
    }

    if (input.name != null && !input.name.equals(rsrc.getSubmitRequirement().name())) {
      throw new BadRequestException("name in input must match name in URL");
    }

    try (MetaDataUpdate md = updateFactory.create(rsrc.getProject().getNameKey())) {
      ProjectConfig config = projectConfigFactory.read(md);

      SubmitRequirement submitRequirement =
          createSubmitRequirement(config, rsrc.getSubmitRequirement().name(), input);

      md.setMessage(String.format("Update Submit Requirement %s", submitRequirement.name()));
      config.commit(md);

      projectCache.evict(rsrc.getProject().getNameKey());

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
    validateSRName(name);
    if (Strings.isNullOrEmpty(input.submittabilityExpression)) {
      throw new BadRequestException("submittability_expression is required");
    }
    if (input.allowOverrideInChildProjects == null) {
      // default is false
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

  private void validateSRName(String name) throws BadRequestException {
    try {
      SubmitRequirementsUtil.validateName(name);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }
}
