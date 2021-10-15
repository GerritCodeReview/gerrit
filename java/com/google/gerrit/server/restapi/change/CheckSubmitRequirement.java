package com.google.gerrit.server.restapi.change;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.common.SubmitRequirementInput;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
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
 */
public class CheckSubmitRequirement
    implements RestModifyView<ChangeResource, SubmitRequirementInput> {
  private final SubmitRequirementsEvaluator evaluator;
  private final SubmitRequirementsJson submitRequirementsJson;

  @Inject
  public CheckSubmitRequirement(
      SubmitRequirementsEvaluator evaluator, SubmitRequirementsJson submitRequirementsJson) {
    this.evaluator = evaluator;
    this.submitRequirementsJson = submitRequirementsJson;
  }

  @Override
  public Response<SubmitRequirementResultInfo> apply(
      ChangeResource resource, SubmitRequirementInput input)
      throws AuthException, BadRequestException, ResourceConflictException, Exception {
    SubmitRequirement requirement = createSubmitRequirement(input);
    SubmitRequirementResult res =
        evaluator.evaluateRequirement(requirement, resource.getChangeData());
    return Response.ok(submitRequirementsJson.toInfo(requirement, res));
  }

  private SubmitRequirement createSubmitRequirement(SubmitRequirementInput input)
      throws BadRequestException {
    validateSubmitRequirementInput(input);
    return SubmitRequirement.builder()
        .setName(input.name)
        .setDescription(Optional.ofNullable(input.description))
        .setSubmittabilityExpression(
            SubmitRequirementExpression.create(input.submittabilityExpression))
        .setAllowOverrideInChildProjects(true) // Not used: we are just checking the requirement
        .build();
  }

  private void validateSubmitRequirementInput(SubmitRequirementInput input)
      throws BadRequestException {
    if (input.name == null) {
      throw new BadRequestException("Field 'name' is missing from input.");
    }
    if (input.submittabilityExpression == null) {
      throw new BadRequestException("Field 'submittability_expression' is missing from input.");
    }
  }
}
