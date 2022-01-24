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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Map;

public interface SubmitRequirementsEvaluator {
  /**
   * Evaluate and return all submit requirement results for a change. Submit requirements are read
   * from the project config of the project containing the change as well as parent projects.
   *
   * @param cd change data corresponding to a specific gerrit change
   * @param includeLegacy if set to true, evaluate legacy {@link
   *     com.google.gerrit.entities.SubmitRecord}s and convert them to submit requirements.
   */
  ImmutableMap<SubmitRequirement, SubmitRequirementResult> evaluateAllRequirements(
      ChangeData cd, boolean includeLegacy);

  /**
   * Evaluate a single {@link SubmitRequirement} using change data. The {@code macros} parameter is
   * used to expand any referenced variables in the submit requirement expressions. See {@link
   * #expandExpression(SubmitRequirementExpression, Map)}.
   */
  SubmitRequirementResult evaluateRequirement(
      SubmitRequirement sr, ChangeData cd, Map<String, String> macros);

  /** Evaluate a {@link SubmitRequirementExpression} using change data. */
  SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData);

  /**
   * Validate a {@link SubmitRequirementExpression}. Callers who wish to validate submit
   * requirements upon creation or update should use this method.
   *
   * @param expression entity containing the expression string.
   * @throws QueryParseException the expression string contains invalid syntax and can't be parsed.
   */
  void validateExpression(SubmitRequirementExpression expression) throws QueryParseException;

  /**
   * Expands the "submit requirement" {@code expression} input using the {@code macros} map. All
   * variables in the expression occurring as ${var_name} will be substituted with the value of that
   * key in the {@code macros} map.
   *
   * <p>Example: macros = {"require-cr", "label:Code-Review=+2"}, expression = "${require-cr}"
   *
   * <p>Output = "label:Code-Review=+2"
   *
   * @throws StorageException if any of the referenced variables in the expression is not defined in
   *     the {@code macros} map.
   */
  SubmitRequirementExpression expandExpression(
      SubmitRequirementExpression expression, Map<String, String> macros) throws StorageException;
}
