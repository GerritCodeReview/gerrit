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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.metrics.Counter2;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/** Evaluates submit requirements for different change data. */
public class SubmitRequirementsEvaluatorImpl implements SubmitRequirementsEvaluator {

  private final Provider<SubmitRequirementChangeQueryBuilder> queryBuilder;
  private final ProjectCache projectCache;
  private final PluginSetContext<SubmitRequirement> globalSubmitRequirements;
  private final Metrics metrics;

  @Singleton
  static class Metrics {
    final Counter2 submitRequirementsMatchingWithLegacy;
    final Counter2 submitRequirementsMismatchingWithLegacy;

    @Inject
    Metrics(MetricMaker metricMaker) {
      submitRequirementsMatchingWithLegacy =
          metricMaker.newCounter(
              "change/submit_requirements/matching_with_legacy",
              new Description(
                      "Total number of times there was a legacy and non-legacy "
                          + "submit requirements with the same name for a change, "
                          + "and the evaluation of both requirements had the same result "
                          + "w.r.t. change submittability.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("project", Metadata.Builder::projectName).build(),
              Field.ofString("srName", Metadata.Builder::submitRequirementName)
                  .description("Submit requirement name")
                  .build());
      submitRequirementsMismatchingWithLegacy =
          metricMaker.newCounter(
              "change/submit_requirements/mismatching_with_legacy",
              new Description(
                      "Total number of times there was a legacy and non-legacy "
                          + "submit requirements with the same name for a change, "
                          + "and the evaluation of both requirements had different result "
                          + "w.r.t. change submittability.")
                  .setRate()
                  .setUnit("count"),
              Field.ofString("project", Metadata.Builder::projectName).build(),
              Field.ofString("srName", Metadata.Builder::indexName)
                  .description("Submit requirement name")
                  .build());
    }
  }

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
      PluginSetContext<SubmitRequirement> globalSubmitRequirements,
      Metrics metrics) {
    this.queryBuilder = queryBuilder;
    this.projectCache = projectCache;
    this.globalSubmitRequirements = globalSubmitRequirements;
    this.metrics = metrics;
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
    if (includeLegacy) {
      Map<SubmitRequirement, SubmitRequirementResult> legacyReqs =
          SubmitRequirementsAdapter.getLegacyRequirements(cd);
      result =
          SubmitRequirementsUtil.mergeLegacyAndNonLegacyRequirements(
              projectConfigRequirements, legacyReqs, cd.project(), metrics);
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

  /**
   * Evaluate and return all {@link SubmitRequirement}s.
   *
   * <p>This includes all globally bound {@link SubmitRequirement}s, as well as requirements stored
   * in this project's config and its parents.
   *
   * <p>The behaviour in case of the name match is controlled by {@link
   * SubmitRequirement#allowOverrideInChildProjects} of global {@link SubmitRequirement}.
   */
  private Map<SubmitRequirement, SubmitRequirementResult> getRequirements(ChangeData cd) {
    Map<String, SubmitRequirement> globalRequirements = getGlobalRequirements();

    ProjectState state = projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
    Map<String, SubmitRequirement> projectConfigRequirements = state.getSubmitRequirements();

    ImmutableMap<String, SubmitRequirement> requirements =
        Stream.concat(
                globalRequirements.entrySet().stream(),
                projectConfigRequirements.entrySet().stream())
            .collect(
                toImmutableMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (globalSubmitRequirement, projectConfigRequirement) ->
                        // Override with projectConfigRequirement if allowed by
                        // globalSubmitRequirement configuration
                        globalSubmitRequirement.allowOverrideInChildProjects()
                            ? projectConfigRequirement
                            : globalSubmitRequirement));
    Map<SubmitRequirement, SubmitRequirementResult> results = new HashMap<>();
    for (SubmitRequirement requirement : requirements.values()) {
      results.put(requirement, evaluateRequirement(requirement, cd));
    }
    return results;
  }

  /**
   * Returns a map of all global {@link SubmitRequirement}s, keyed by their lower-case name.
   *
   * <p>The global {@link SubmitRequirement}s apply to all projects and can be bound by plugins.
   */
  private Map<String, SubmitRequirement> getGlobalRequirements() {
    return globalSubmitRequirements.stream()
        .collect(
            toImmutableMap(
                globalRequirement -> globalRequirement.name().toLowerCase(), Function.identity()));
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
