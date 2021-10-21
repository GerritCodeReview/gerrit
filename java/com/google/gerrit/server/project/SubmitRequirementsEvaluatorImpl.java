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
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.gerrit.server.experiments.ExperimentFeaturesConstants;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Evaluates submit requirements for different change data. */
public class SubmitRequirementsEvaluatorImpl implements SubmitRequirementsEvaluator {

  private final Provider<SubmitRequirementChangeQueryBuilder> queryBuilder;
  private final ProjectCache projectCache;
  private final SubmitRuleEvaluator.Factory legacyEvaluator;
  private final ExperimentFeatures experimentFeatures;

  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(SubmitRequirementsEvaluator.class)
            .to(SubmitRequirementsEvaluatorImpl.class)
            .in(Scopes.SINGLETON);
      }
    };
  }

  @Inject
  private SubmitRequirementsEvaluatorImpl(
      Provider<SubmitRequirementChangeQueryBuilder> queryBuilder,
      ProjectCache projectCache,
      SubmitRuleEvaluator.Factory legacyEvaluator,
      ExperimentFeatures experimentFeatures) {
    this.queryBuilder = queryBuilder;
    this.projectCache = projectCache;
    this.legacyEvaluator = legacyEvaluator;
    this.experimentFeatures = experimentFeatures;
  }

  @Override
  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    queryBuilder.get().parse(expression.expressionString());
  }

  @Override
  public Map<SubmitRequirement, SubmitRequirementResult> evaluateAllRequirements(
      ChangeData cd, boolean includeLegacy) {
    Map<SubmitRequirement, SubmitRequirementResult> projectConfigRequirements = getRequirements(cd);
    Map<SubmitRequirement, SubmitRequirementResult> result = projectConfigRequirements;
    if (includeLegacy
        && experimentFeatures.isFeatureEnabled(
            ExperimentFeaturesConstants
                .GERRIT_BACKEND_REQUEST_FEATURE_ENABLE_LEGACY_SUBMIT_REQUIREMENTS)) {
      Map<SubmitRequirement, SubmitRequirementResult> legacyReqs =
          SubmitRequirementsAdapter.getLegacyRequirements(legacyEvaluator, cd);
      result =
          SubmitRequirementsUtil.mergeLegacyAndNonLegacyRequirements(
              projectConfigRequirements, legacyReqs);
    }
    return ImmutableMap.copyOf(result);
  }

  @Override
  public SubmitRequirementResult evaluateRequirement(SubmitRequirement sr, ChangeData cd) {
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
        .legacy(Optional.of(false))
        .submitRequirement(sr)
        .patchSetCommitId(cd.currentPatchSet().commitId())
        .submittabilityExpressionResult(blockingResult)
        .applicabilityExpressionResult(applicabilityResult)
        .overrideExpressionResult(overrideResult)
        .build();
  }

  @Override
  public SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData) {
    try {
      Predicate<ChangeData> predicate = queryBuilder.get().parse(expression.expressionString());
      PredicateResult predicateResult = evaluatePredicateTree(predicate, changeData);
      return SubmitRequirementExpressionResult.create(expression, predicateResult);
    } catch (QueryParseException e) {
      return SubmitRequirementExpressionResult.error(expression, e.getMessage());
    }
  }

  /** Evaluate and return submit requirements stored in this project's config and its parents. */
  private Map<SubmitRequirement, SubmitRequirementResult> getRequirements(ChangeData cd) {
    ProjectState state = projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
    Map<String, SubmitRequirement> requirements = state.getSubmitRequirements();
    Map<SubmitRequirement, SubmitRequirementResult> result = new HashMap<>();
    for (SubmitRequirement requirement : requirements.values()) {
      result.put(requirement, evaluateRequirement(requirement, cd));
    }
    return result;
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
