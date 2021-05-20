package com.google.gerrit.server.project;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.index.query.QueryParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class SubmitRequirementExpressionsValidator {
  private final SubmitRequirementsEvaluator submitRequirementsEvaluator;

  @Inject
  SubmitRequirementExpressionsValidator(SubmitRequirementsEvaluator submitRequirementsEvaluator) {
    this.submitRequirementsEvaluator = submitRequirementsEvaluator;
  }

  /**
   * Validates the query expressions on the input {@code submitRequirement}.
   *
   * @return list of string containing the error messages resulting from the validation. The list is
   *     empty if the "submit requirement" is valid.
   */
  public ImmutableList<String> validateExpressions(SubmitRequirement submitRequirement) {
    List<String> validationMessages = new ArrayList<>();
    validateSubmitRequirementExpression(
        validationMessages,
        submitRequirement,
        submitRequirement.submittabilityExpression(),
        ProjectConfig.KEY_SR_SUBMITTABILITY_EXPRESSION);
    submitRequirement
        .applicabilityExpression()
        .ifPresent(
            expression ->
                validateSubmitRequirementExpression(
                    validationMessages,
                    submitRequirement,
                    expression,
                    ProjectConfig.KEY_SR_APPLICABILITY_EXPRESSION));
    submitRequirement
        .overrideExpression()
        .ifPresent(
            expression ->
                validateSubmitRequirementExpression(
                    validationMessages,
                    submitRequirement,
                    expression,
                    ProjectConfig.KEY_SR_OVERRIDE_EXPRESSION));
    return ImmutableList.copyOf(validationMessages);
  }

  private void validateSubmitRequirementExpression(
      List<String> validationMessages,
      SubmitRequirement submitRequirement,
      SubmitRequirementExpression expression,
      String configKey) {
    try {
      submitRequirementsEvaluator.validateExpression(expression);
    } catch (QueryParseException e) {
      if (validationMessages.isEmpty()) {
        validationMessages.add("Invalid project configuration");
      }
      validationMessages.add(
          String.format(
              "  %s: Expression '%s' of submit requirement '%s' (parameter %s.%s.%s) is"
                  + " invalid: %s",
              ProjectConfig.PROJECT_CONFIG,
              expression.expressionString(),
              submitRequirement.name(),
              ProjectConfig.SUBMIT_REQUIREMENT,
              submitRequirement.name(),
              configKey,
              e.getMessage()));
    }
  }
}
