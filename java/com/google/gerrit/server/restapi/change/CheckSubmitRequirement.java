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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.SubmitRequirementsJson;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.inject.Inject;
import java.util.Optional;

/**
 * A rest view to evaluate (test) a {@link com.google.gerrit.entities.SubmitRequirement} on a given
 * change.
 *
 * <p>TODO(ghareeb): Can this class be made singleton?
 */
public class CheckSubmitRequirement
    implements RestModifyView<ChangeResource, SubmitRequirementInput> {
  private final SubmitRequirementsEvaluator evaluator;

  @Inject
  public CheckSubmitRequirement(SubmitRequirementsEvaluator evaluator) {
    this.evaluator = evaluator;
  }

  @Override
  public Response<SubmitRequirementResultInfo> apply(
      ChangeResource resource, SubmitRequirementInput input) throws BadRequestException {
    SubmitRequirement requirement = createSubmitRequirement(input);
    SubmitRequirementResult res =
        evaluator.evaluateRequirement(requirement, resource.getChangeData());
    return Response.ok(SubmitRequirementsJson.toInfo(requirement, res));
  }

  private SubmitRequirement createSubmitRequirement(SubmitRequirementInput input)
      throws BadRequestException {
    validateSubmitRequirementInput(input);
    return SubmitRequirement.builder()
        .setName(input.name)
        .setDescription(Optional.ofNullable(input.description))
        .setApplicabilityExpression(SubmitRequirementExpression.of(input.applicability_expression))
        .setSubmittabilityExpression(
            SubmitRequirementExpression.create(input.submittability_expression))
        .setOverrideExpression(SubmitRequirementExpression.of(input.override_expression))
        .setAllowOverrideInChildProjects(
            input.allowOverrideInChildProjects == null ? true : input.allowOverrideInChildProjects)
        .build();
  }

  private void validateSubmitRequirementInput(SubmitRequirementInput input)
      throws BadRequestException {
    if (input.name == null) {
      throw new BadRequestException("Field 'name' is missing from input.");
    }
    if (input.submittability_expression == null) {
      throw new BadRequestException("Field 'submittability_expression' is missing from input.");
    }
  }
}
