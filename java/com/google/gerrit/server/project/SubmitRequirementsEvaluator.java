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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Optional;

/** Evaluates submit requirements for different change data. */
public class SubmitRequirementsEvaluator {
  private final Provider<ChangeQueryBuilder> changeQueryBuilderProvider;

  @Inject
  private SubmitRequirementsEvaluator(Provider<ChangeQueryBuilder> changeQueryBuilderProvider) {
    this.changeQueryBuilderProvider = changeQueryBuilderProvider;
  }

  /**
   * Validate a {@link SubmitRequirementExpression}.
   *
   * @param expression entity containing the expression string.
   * @throws QueryParseException the expression string contains invalid syntax and can't be parsed.
   */
  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    changeQueryBuilderProvider.get().parse(expression.expressionString());
  }

  /**
   * Evaluates a {@link SubmitRequirement} on a given {@link ChangeData}.
   *
   * @throws QueryParseException Any of the {@link SubmitRequirement#applicabilityExpression()},
   *     {@link SubmitRequirement#submittabilityExpression()} or {@link
   *     SubmitRequirement#overrideExpression()} contain invalid syntax and cannot be parsed.
   */
  public SubmitRequirementResult evaluate(SubmitRequirement sr, ChangeData cd)
      throws QueryParseException {
    Optional<SubmitRequirementExpressionResult> submittabilityResult =
        evaluateExpression(Optional.of(sr.submittabilityExpression()), cd);

    Optional<SubmitRequirementExpressionResult> applicabilityResult =
        evaluateExpression(sr.applicabilityExpression(), cd);

    Optional<SubmitRequirementExpressionResult> overrideResult =
        evaluateExpression(sr.overrideExpression(), cd);

    SubmitRequirementResult.Builder result =
        SubmitRequirementResult.builder()
            .submittabilityExpressionResult(submittabilityResult.get())
            .applicabilityExpressionResult(applicabilityResult)
            .overrideExpressionResult(overrideResult);

    if (applicabilityResult.isPresent() && applicabilityResult.get().status() == false) {
      result.status(SubmitRequirementResult.Status.NOT_APPLICABLE);
    } else if (overrideResult.isPresent() && overrideResult.get().status() == true) {
      result.status(SubmitRequirementResult.Status.OVERRIDDEN);
    } else if (submittabilityResult.get().status() == true) {
      result.status(SubmitRequirementResult.Status.SATISFIED);
    } else {
      result.status(SubmitRequirementResult.Status.UNSATISFIED);
    }

    return result.build();
  }

  /**
   * Evaluate a {@link SubmitRequirementExpression} using change data.
   *
   * @throws QueryParseException the expression string contains invalid syntax and can't be parsed.
   */
  public Optional<SubmitRequirementExpressionResult> evaluateExpression(
      Optional<SubmitRequirementExpression> expression, ChangeData changeData)
      throws QueryParseException {
    if (!expression.isPresent() || expression.get().expressionString().isEmpty()) {
      return Optional.empty();
    }
    Predicate<ChangeData> predicate =
        changeQueryBuilderProvider.get().parse(expression.get().expressionString());
    PredicateResult predicateResult = evaluatePredicateTree(predicate, changeData);
    return Optional.of(
        SubmitRequirementExpressionResult.builder().predicateResult(predicateResult).build());
  }

  /** Evaluate the predicate recursively using change data. */
  private PredicateResult evaluatePredicateTree(
      Predicate<ChangeData> predicate, ChangeData changeData) {
    PredicateResult.Builder predicateResult =
        PredicateResult.builder()
            .predicateString(predicate.toString())
            .status(predicate.asMatchable().match(changeData));
    predicate
        .getChildren()
        .forEach(c -> predicateResult.addChildPredicate(evaluatePredicateTree(c, changeData)));
    return predicateResult.build();
  }
}
