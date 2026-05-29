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
