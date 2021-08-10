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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableMap;
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
import com.google.inject.Singleton;
import java.util.Map;
import java.util.Optional;

/** Evaluates submit requirements for different change data. */
@Singleton
public class SubmitRequirementsEvaluator {
  private final Provider<ChangeQueryBuilder> changeQueryBuilderProvider;
  private final ProjectCache projectCache;

  @Inject
  private SubmitRequirementsEvaluator(
      Provider<ChangeQueryBuilder> changeQueryBuilderProvider, ProjectCache projectCache) {
    this.changeQueryBuilderProvider = changeQueryBuilderProvider;
    this.projectCache = projectCache;
  }

  /**
   * Validate a {@link SubmitRequirementExpression}. Callers who wish to validate submit
   * requirements upon creation or update should use this method.
   *
   * @param expression entity containing the expression string.
   * @throws QueryParseException the expression string contains invalid syntax and can't be parsed.
   */
  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    changeQueryBuilderProvider.get().parse(expression.expressionString());
  }

  /**
   * Evaluate and return all submit requirements for a change. Submit requirements are retrieved for
   * the project containing the change and parent projects as well.
   */
  public Map<SubmitRequirement, SubmitRequirementResult> getResults(ChangeData cd) {
    ProjectState state = projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
    Map<String, SubmitRequirement> requirements = state.getSubmitRequirements();
    ImmutableMap.Builder<SubmitRequirement, SubmitRequirementResult> result =
        ImmutableMap.builderWithExpectedSize(requirements.size());
    for (SubmitRequirement requirement : requirements.values()) {
      result.put(requirement, evaluate(requirement, cd));
    }
    return result.build();
  }

  /** Evaluate a {@link SubmitRequirement} on a given {@link ChangeData}. */
  public SubmitRequirementResult evaluate(SubmitRequirement sr, ChangeData cd) {
    SubmitRequirementExpressionResult blockingResult =
        evaluateExpression(sr.submittabilityExpression(), cd);

    Optional<SubmitRequirementExpressionResult> applicabilityResult =
        sr.applicabilityExpression().isPresent()
            ? Optional.of(evaluateExpression(sr.applicabilityExpression().get(), cd))
            : Optional.empty();

    Optional<SubmitRequirementExpressionResult> overrideResult =
        sr.overrideExpression().isPresent()
            ? Optional.of(evaluateExpression(sr.overrideExpression().get(), cd))
            : Optional.empty();

    return SubmitRequirementResult.builder()
        .submitRequirement(sr)
        .patchSetCommitId(cd.currentPatchSet().commitId())
        .submittabilityExpressionResult(blockingResult)
        .applicabilityExpressionResult(applicabilityResult)
        .overrideExpressionResult(overrideResult)
        .build();
  }

  /** Evaluate a {@link SubmitRequirementExpression} using change data. */
  public SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData) {
    try {
      Predicate<ChangeData> predicate =
          changeQueryBuilderProvider.get().parse(expression.expressionString());
      PredicateResult predicateResult = evaluatePredicateTree(predicate, changeData);
      return SubmitRequirementExpressionResult.create(expression, predicateResult);
    } catch (QueryParseException e) {
      return SubmitRequirementExpressionResult.error(expression, e.getMessage());
    }
  }

  /** Evaluate the predicate recursively using change data. */
  private PredicateResult evaluatePredicateTree(
      Predicate<ChangeData> predicate, ChangeData changeData) {
    PredicateResult.Builder predicateResult =
        PredicateResult.builder()
            .predicateString(predicate.isLeaf() ? predicate.getPredicateString() : "")
            .status(predicate.asMatchable().match(changeData));
    predicate
        .getChildren()
        .forEach(
            c -> predicateResult.addChildPredicateResult(evaluatePredicateTree(c, changeData)));
    return predicateResult.build();
  }
}
