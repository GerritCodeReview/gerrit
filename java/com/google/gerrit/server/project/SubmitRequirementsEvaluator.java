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

import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class SubmitRequirementsEvaluator {
  private final Provider<ChangeQueryBuilder> changeQueryBuilderProvider;

  @Inject
  private SubmitRequirementsEvaluator(Provider<ChangeQueryBuilder> changeQueryBuilderProvider) {
    this.changeQueryBuilderProvider = changeQueryBuilderProvider;
  }

  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    changeQueryBuilderProvider.get().parse(expression.expressionString());
  }

  public SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData) throws QueryParseException {
    Predicate<ChangeData> predicate =
        changeQueryBuilderProvider.get().parse(expression.expressionString());
    PredicateResult predicateResult = evaluatePredicateTree(predicate, changeData);
    return SubmitRequirementExpressionResult.builder().predicateResult(predicateResult).build();
  }

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
