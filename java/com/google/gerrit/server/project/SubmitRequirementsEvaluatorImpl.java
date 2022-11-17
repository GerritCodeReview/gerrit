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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.PredicateResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scopes;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/** Evaluates submit requirements for different change data. */
public class SubmitRequirementsEvaluatorImpl implements SubmitRequirementsEvaluator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Provider<SubmitRequirementChangeQueryBuilder> queryBuilder;
  private final ProjectCache projectCache;
  private final PluginSetContext<SubmitRequirement> globalSubmitRequirements;
  private final OneOffRequestContext requestContext;

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
      OneOffRequestContext requestContext) {
    this.queryBuilder = queryBuilder;
    this.projectCache = projectCache;
    this.globalSubmitRequirements = globalSubmitRequirements;
    this.requestContext = requestContext;
  }

  @Override
  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    queryBuilder.get().parse(expression.expressionString());
  }

  @Override
  public ImmutableMap<SubmitRequirement, SubmitRequirementResult> evaluateAllRequirements(
      ChangeData cd) {
    return getRequirements(cd);
  }

  @Override
  public SubmitRequirementResult evaluateRequirement(SubmitRequirement sr, ChangeData cd) {
    try (ManualRequestContext ignored = requestContext.open()) {
      // Use a request context to execute predicates as an internal user with expanded visibility.
      // This is so that the evaluation does not depend on who is running the current request (e.g.
      // a "ownerin" predicate with group that is not visible to the person making this request).

      Optional<SubmitRequirementExpressionResult> applicabilityResult =
          sr.applicabilityExpression().isPresent()
              ? Optional.of(evaluateExpression(sr.applicabilityExpression().get(), cd))
              : Optional.empty();
      Optional<SubmitRequirementExpressionResult> submittabilityResult =
          Optional.of(
              SubmitRequirementExpressionResult.notEvaluated(sr.submittabilityExpression()));
      Optional<SubmitRequirementExpressionResult> overrideResult =
          sr.overrideExpression().isPresent()
              ? Optional.of(
                  SubmitRequirementExpressionResult.notEvaluated(sr.overrideExpression().get()))
              : Optional.empty();
      if (!sr.applicabilityExpression().isPresent()
          || SubmitRequirementResult.assertPass(applicabilityResult)) {
        submittabilityResult = Optional.of(evaluateExpression(sr.submittabilityExpression(), cd));
        overrideResult =
            sr.overrideExpression().isPresent()
                ? Optional.of(evaluateExpression(sr.overrideExpression().get(), cd))
                : Optional.empty();
      }

      if (applicabilityResult.isPresent()) {
        logger.atFine().log(
            "Applicability expression result for SR name '%s':"
                + " passing atoms: %s, failing atoms: %s",
            sr.name(),
            applicabilityResult.get().passingAtoms(),
            applicabilityResult.get().failingAtoms());
      }
      if (submittabilityResult.isPresent()) {
        logger.atFine().log(
            "Submittability expression result for SR name '%s':"
                + " passing atoms: %s, failing atoms: %s",
            sr.name(),
            submittabilityResult.get().passingAtoms(),
            submittabilityResult.get().failingAtoms());
      }
      if (overrideResult.isPresent()) {
        logger.atFine().log(
            "Override expression result for SR name '%s':"
                + " passing atoms: %s, failing atoms: %s",
            sr.name(), overrideResult.get().passingAtoms(), overrideResult.get().failingAtoms());
      }

      return SubmitRequirementResult.builder()
          .legacy(Optional.of(false))
          .submitRequirement(sr)
          .patchSetCommitId(cd.currentPatchSet().commitId())
          .submittabilityExpressionResult(submittabilityResult)
          .applicabilityExpressionResult(applicabilityResult)
          .overrideExpressionResult(overrideResult)
          .build();
    }
  }

  @Override
  public SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData) {
    try {
      Predicate<ChangeData> predicate = queryBuilder.get().parse(expression.expressionString());
      PredicateResult predicateResult = evaluatePredicateTree(predicate, changeData);
      return SubmitRequirementExpressionResult.create(expression, predicateResult);
    } catch (QueryParseException | SubmitRequirementEvaluationException e) {
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
  private ImmutableMap<SubmitRequirement, SubmitRequirementResult> getRequirements(ChangeData cd) {
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
    ImmutableMap.Builder<SubmitRequirement, SubmitRequirementResult> results =
        ImmutableMap.builder();
    for (SubmitRequirement requirement : requirements.values()) {
      results.put(requirement, evaluateRequirement(requirement, cd));
    }
    return results.build();
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
