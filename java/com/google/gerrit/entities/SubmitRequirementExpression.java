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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import java.util.Optional;

/**
 * Describe a applicability, blocking or override expression of a {@link SubmitRequirement}.
 *
 * @param expressionString Returns the underlying String representing this {@link
 *     SubmitRequirementExpression}.
 */
public record SubmitRequirementExpression(String expressionString) {
  public SubmitRequirementExpression {
    requireNonNull(expressionString, "expressionString");
  }

  public static SubmitRequirementExpression create(String expression) {
    return new SubmitRequirementExpression(expression);
  }

  /**
   * Creates a new {@link SubmitRequirementExpression}.
   *
   * @param expression String representation of the expression
   * @return empty {@link Optional} if the input expression is null or empty, or an Optional
   *     containing the expression otherwise.
   */
  public static Optional<SubmitRequirementExpression> of(@Nullable String expression) {
    return Optional.ofNullable(Strings.emptyToNull(expression))
        .map(SubmitRequirementExpression::create);
  }

  /**
   * Returns a "submit requirement" expression that requires the maximum vote on the Code-Review
   * label.
   */
  public static SubmitRequirementExpression maxCodeReview() {
    return SubmitRequirementExpression.create(String.format("label:Code-Review=MAX"));
  }
}
