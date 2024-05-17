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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.extensions.common.SubmitRequirementInfo;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.SubmitRequirementExpressionsValidator;
import com.google.gerrit.server.project.SubmitRequirementJson;
import com.google.gerrit.server.project.SubmitRequirementResource;
import com.google.gerrit.server.project.SubmitRequirementsUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A rest modify view that updates the definition of an existing submit requirement for a project.
 */
@Singleton
public class UpdateSubmitRequirement
    implements RestModifyView<SubmitRequirementResource, SubmitRequirementInput> {
  private final SubmitRequirementExpressionsValidator submitRequirementExpressionsValidator;
  private final RepoMetaDataUpdater repoMetaDataUpdater;

  @Inject
  public UpdateSubmitRequirement(
      SubmitRequirementExpressionsValidator submitRequirementExpressionsValidator,
      RepoMetaDataUpdater repoMetaDataUpdater) {
    this.submitRequirementExpressionsValidator = submitRequirementExpressionsValidator;
    this.repoMetaDataUpdater = repoMetaDataUpdater;
  }

  @Override
  public Response<SubmitRequirementInfo> apply(
      SubmitRequirementResource rsrc, SubmitRequirementInput input)
      throws AuthException, BadRequestException, PermissionBackendException, IOException {
    if (input == null) {
      input = new SubmitRequirementInput();
    }

    if (input.name != null && !input.name.equals(rsrc.getSubmitRequirement().name())) {
      throw new BadRequestException("name in input must match name in URL");
    }

    try (var configUpdater =
        repoMetaDataUpdater.configUpdater(
            rsrc.getProject().getNameKey(),
            null,
            String.format("Update Submit Requirement %s", rsrc.getSubmitRequirement().name()))) {
      ProjectConfig config = configUpdater.getConfig();

      SubmitRequirement submitRequirement =
          updateSubmitRequirement(config, rsrc.getSubmitRequirement().name(), input);

      configUpdater.commitConfigUpdate();

      return Response.created(SubmitRequirementJson.format(submitRequirement));
    } catch (ConfigInvalidException e) {
      throw new IOException("Failed to read project config", e);
    }
  }

  public SubmitRequirement updateSubmitRequirement(
      ProjectConfig config, String name, SubmitRequirementInput input) throws BadRequestException {
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

    ImmutableList<String> validationMessages =
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
