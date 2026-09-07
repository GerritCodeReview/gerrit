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
import static com.google.gerrit.index.query.QueryBuilder.findFieldInParsedQuery;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PredicateResult;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.RegexQueryPermissionChecker;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.permissions.RegexPermissionPolicy;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Config;

/** Evaluates submit requirements for different change data. */
public class SubmitRequirementsEvaluatorImpl implements SubmitRequirementsEvaluator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory;
  private final ProjectCache projectCache;
  private final PluginSetContext<SubmitRequirement> globalSubmitRequirements;
  private final Config config;
  private final boolean requireOperatorForUpdate;
  private final boolean requireOperatorForEvaluation;
  private final ExecutorService executor;
  private final OneOffRequestContext requestContext;
  private final Provider<CurrentUser> currentUser;
  private final long executionTimeout;
  private final SubmitRequirementRegexQueryPermissionChecker regexQueryPermissionChecker;

  public static Module module() {
    return new FactoryModule() {
      @Override
      protected void configure() {
        bind(SubmitRequirementsEvaluator.class)
            .to(SubmitRequirementsEvaluatorImpl.class)
            .in(Scopes.SINGLETON);

        factory(SubmitRequirementChangeQueryBuilder.Factory.class);
      }

      @Provides
      @Singleton
      @SubmitRequirementExecutor
      ExecutorService provideSubmitRequirementExecutor(
          WorkQueue workQueue, @GerritServerConfig Config config) {
        int evaluationThreads = config.getInt("submitRequirement", null, "evaluationThreads", -1);

        if (evaluationThreads < 0) {
          return MoreExecutors.newDirectExecutorService();
        }

        if (evaluationThreads == 0) {
          evaluationThreads = Runtime.getRuntime().availableProcessors();
        }

        return MoreExecutors.listeningDecorator(
            workQueue.createQueue(evaluationThreads, "submit-requirement-evaluator"));
      }
    };
  }

  public static class SubmitRequirementRegexQueryPermissionChecker
      extends RegexQueryPermissionChecker {
    // Keep this list in sync with submit-requirement operators that always compile regexes.
    private static final Set<String> REGEX_FIELDS_NAMES =
        Set.of("authoremail", "committeremail", "uploaderemail");

    @Inject
    SubmitRequirementRegexQueryPermissionChecker(
        Provider<RegexPermissionPolicy> regexPermissionPolicyProvider) {
      super(regexPermissionPolicyProvider);
    }

    @Override
    public boolean containsRegexInQuery(String query) throws QueryParseException {
      return super.containsRegexInQuery(query)
          || findFieldInParsedQuery(
              query, (field) -> REGEX_FIELDS_NAMES.contains(field.toLowerCase(Locale.ROOT)));
    }
  }

  @Inject
  public SubmitRequirementsEvaluatorImpl(
      SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory,
      ProjectCache projectCache,
      PluginSetContext<SubmitRequirement> globalSubmitRequirements,
      @GerritServerConfig Config config,
      Provider<CurrentUser> currentUser,
      OneOffRequestContext requestContext,
      @SubmitRequirementExecutor ExecutorService executor,
      SubmitRequirementRegexQueryPermissionChecker regexQueryPermissionChecker) {
    this.queryBuilderFactory = queryBuilderFactory;
    this.projectCache = projectCache;
    this.globalSubmitRequirements = globalSubmitRequirements;
    this.config = config;
    this.currentUser = currentUser;
    this.requestContext = requestContext;
    this.regexQueryPermissionChecker = regexQueryPermissionChecker;
    this.requireOperatorForUpdate = requireOperatorForUpdate();
    this.requireOperatorForEvaluation = requireOperatorForEvaluation();
    this.executor = executor;
    this.executionTimeout =
        config.getTimeUnit("submitRequirement", null, "executionTimeout", 0, TimeUnit.MILLISECONDS);
  }

  @Override
  public void validateExpression(SubmitRequirementExpression expression)
      throws QueryParseException {
    if (!regexQueryPermissionChecker.isAllowed()) {
      regexQueryPermissionChecker.check(expression.expressionString());
    }

    // Use a request context to execute predicates as an internal user with expanded visibility.
    // This is so that the evaluation does not depend on who is running the current request (e.g.
    // a "ownerin" predicate with group that is not visible to the person making this request).
    try (ManualRequestContext ignored = requestContext.open()) {
      @SuppressWarnings("unused")
      var unused =
          queryBuilderFactory.create(requireOperatorForUpdate).parse(expression.expressionString());
    }
  }

  private boolean requireOperatorForUpdate() {
    return requiredOperator("requireOperatorForUpdate");
  }

  private boolean requireOperatorForEvaluation() {
    return requiredOperator("requireOperatorForEvaluation");
  }

  private boolean requiredOperator(String configName) {
    return config.getBoolean("submitRequirement", null, configName, false);
  }

  @Override
  public ImmutableMap<SubmitRequirement, SubmitRequirementResult> evaluateAllRequirements(
      ChangeData cd) {
    // This method is used to return the full set of submit requirements associated with a
    // change, therefore it must return the same information and result regardless of the
    // user that is running the request (e.g. a "ownerin" predicate with group that is not visible
    // to the person making this request).
    try (ManualRequestContext ignored = requestContext.open()) {
      return getRequirements(cd);
    }
  }

  @Override
  public SubmitRequirementResult evaluateRequirement(SubmitRequirement sr, ChangeData cd) {
    // This method is never used in Gerrit production code, however, its JavaDoc asserts that it
    // should be executed by running inside an internal user request context.
    try (ManualRequestContext ignored = requestContext.open()) {
      return evaluateRequirementInternal(sr, cd);
    }
  }

  @Override
  public SubmitRequirementResult evaluateRequirementWithCurrentUser(
      SubmitRequirement sr, ChangeData cd) throws QueryParseException {
    if (!regexQueryPermissionChecker.isAllowed()) {
      checkRegexPermission(sr);
    }

    // This method is called from the /changes/<change-id>/check.submit_requirement REST-API
    // which is evaluating a user-crafted submit requirement against a change in Gerrit.
    // Because of the nature of the request and the lack of trust of the remote user
    // performing the request, executing the operation using an internal user context
    // would represent a security risk: the code and expressions used in the submit
    // requirement passed have not been reviewed or approved by anyone and could either
    // cause data leak or overload to the Gerrit server.
    //
    // Execute the submit requirement using the current user context so that any visibility
    // or restrictions are taken into account when evaluating it.
    return evaluateRequirementInternal(sr, cd);
  }

  private void checkRegexPermission(SubmitRequirement submitRequirement)
      throws QueryParseException {
    regexQueryPermissionChecker.check(
        submitRequirement.submittabilityExpression().expressionString());
    if (submitRequirement.applicabilityExpression().isPresent()) {
      regexQueryPermissionChecker.check(
          submitRequirement.applicabilityExpression().get().expressionString());
    }
    if (submitRequirement.overrideExpression().isPresent()) {
      regexQueryPermissionChecker.check(
          submitRequirement.overrideExpression().get().expressionString());
    }
  }

  /** Evaluate a {@link SubmitRequirementExpression} using change data. */
  @VisibleForTesting
  public SubmitRequirementExpressionResult evaluateExpression(
      SubmitRequirementExpression expression, ChangeData changeData) {
    try {
      Predicate<ChangeData> predicate =
          queryBuilderFactory
              .create(requireOperatorForEvaluation)
              .parse(expression.expressionString());
      PredicateResult predicateResult = changeData.evaluatePredicateTree(predicate);
      return SubmitRequirementExpressionResult.create(expression, predicateResult);
    } catch (QueryParseException
        | SubmitRequirementEvaluationException
        | IllegalArgumentException e) {
      logger.atWarning().withCause(e).log(
          "Failed to evaluate submit requirement expression: %s", expression.expressionString());
      return SubmitRequirementExpressionResult.error(expression, e.getMessage());
    }
  }

  private SubmitRequirementResult evaluateRequirementInternal(SubmitRequirement sr, ChangeData cd) {
    Optional<Account.Id> userAccountId = getCurrentAccountId();
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Evaluate submit requirement " + sr.name(),
            Metadata.builder().changeId(cd.change().getId().get()).build())) {
      Callable<SubmitRequirementResult> task =
          () -> {
            try (ManualRequestContext ctx =
                userAccountId.map(requestContext::openAs).orElseGet(requestContext::open)) {
              Optional<SubmitRequirementExpressionResult> applicabilityResult =
                  sr.applicabilityExpression().isPresent()
                      ? Optional.of(evaluateExpression(sr.applicabilityExpression().get(), cd))
                      : Optional.empty();

              Optional<SubmitRequirementExpressionResult> submittabilityResult =
                  Optional.of(
                      SubmitRequirementExpressionResult.notEvaluated(
                          sr.submittabilityExpression()));

              Optional<SubmitRequirementExpressionResult> overrideResult =
                  sr.overrideExpression().isPresent()
                      ? Optional.of(
                          SubmitRequirementExpressionResult.notEvaluated(
                              sr.overrideExpression().get()))
                      : Optional.empty();

              if (!sr.applicabilityExpression().isPresent()
                  || SubmitRequirementResult.assertPass(applicabilityResult)) {
                submittabilityResult =
                    Optional.of(evaluateExpression(sr.submittabilityExpression(), cd));
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
                    sr.name(),
                    overrideResult.get().passingAtoms(),
                    overrideResult.get().failingAtoms());
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
          };
      Future<SubmitRequirementResult> future = executor.submit(task);

      try {
        return future.get(executionTimeout, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        future.cancel(true);
        logger.atWarning().log("Submit requirement '%s' evaluation timed out", sr.name());

        return timeoutResult(sr, cd);
      } catch (ExecutionException | InterruptedException e) {
        logger.atSevere().withCause(e).log("Error evaluating Submit requirement: %s", sr.name());
        return errorResult(sr, cd, e);
      }
    }
  }

  private Optional<Account.Id> getCurrentAccountId() {
    try {
      CurrentUser user = currentUser.get();
      return user.isIdentifiedUser()
          ? Optional.of(user.asIdentifiedUser().getAccountId())
          : Optional.empty();
    } catch (OutOfScopeException | ProvisionException e) {
      // Some non-request callers, such as ChangeIndexer, deliberately expose no scoped user.
      logger.atFiner().withCause(e).log("Unable to resolve user");
      return Optional.empty();
    }
  }

  private SubmitRequirementResult timeoutResult(SubmitRequirement sr, ChangeData cd) {
    SubmitRequirementExpressionResult timeout =
        SubmitRequirementExpressionResult.create(
            sr.submittabilityExpression(),
            SubmitRequirementExpressionResult.Status.TIMEOUT,
            ImmutableList.of(),
            ImmutableList.of("Execution timeout exceeded"));

    return SubmitRequirementResult.builder()
        .legacy(Optional.of(false))
        .submitRequirement(sr)
        .patchSetCommitId(cd.currentPatchSet().commitId())
        .submittabilityExpressionResult(Optional.of(timeout))
        .applicabilityExpressionResult(
            sr.applicabilityExpression().map(SubmitRequirementExpressionResult::notEvaluated))
        .overrideExpressionResult(
            sr.overrideExpression().map(SubmitRequirementExpressionResult::notEvaluated))
        .build();
  }

  private SubmitRequirementResult errorResult(SubmitRequirement sr, ChangeData cd, Throwable e) {
    String msg =
        (e instanceof ExecutionException && e.getCause() != null)
            ? e.getCause().getMessage()
            : e.getMessage();
    if (msg == null) {
      msg = e.toString();
    }
    SubmitRequirementExpressionResult error =
        SubmitRequirementExpressionResult.error(sr.submittabilityExpression(), msg);

    return SubmitRequirementResult.builder()
        .legacy(Optional.of(false))
        .submitRequirement(sr)
        .patchSetCommitId(cd.currentPatchSet().commitId())
        .submittabilityExpressionResult(Optional.of(error))
        .applicabilityExpressionResult(
            sr.applicabilityExpression().map(SubmitRequirementExpressionResult::notEvaluated))
        .overrideExpressionResult(
            sr.overrideExpression().map(SubmitRequirementExpressionResult::notEvaluated))
        .build();
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
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Evaluate submit requirements",
            Metadata.builder().changeId(cd.change().getId().get()).build())) {
      ImmutableMap<String, SubmitRequirement> globalRequirements;
      Map<String, SubmitRequirement> projectConfigRequirements;
      try (TraceTimer timer2 =
          TraceContext.newTimer(
              "Read submit requirement definitions",
              Metadata.builder().changeId(cd.change().getId().get()).build())) {
        globalRequirements = getGlobalRequirements();
        ProjectState state = projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
        projectConfigRequirements = state.getSubmitRequirements();
      }

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
        results.put(requirement, evaluateRequirementInternal(requirement, cd));
      }
      return results.build();
    }
  }

  /**
   * Returns a map of all global {@link SubmitRequirement}s, keyed by their lower-case name.
   *
   * <p>The global {@link SubmitRequirement}s apply to all projects and can be bound by plugins.
   */
  private ImmutableMap<String, SubmitRequirement> getGlobalRequirements() {
    return globalSubmitRequirements.stream()
        .collect(
            toImmutableMap(
                globalRequirement -> globalRequirement.name().toLowerCase(Locale.US),
                Function.identity()));
  }
}
