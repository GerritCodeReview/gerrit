// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.entities.PredicateResult;
import com.google.gerrit.entities.SubmitTypeOverride;
import com.google.gerrit.entities.SubmitTypeOverrideExpression;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.gerrit.server.rules.PrologSubmitRuleUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Evaluates which {@link SubmitType} is applicable for the given change. */
public class SubmitTypeEvaluator {
  @Singleton
  private static class Metrics {
    final Timer0 submitTypeEvaluationLatency;

    @Inject
    Metrics(MetricMaker metricMaker) {
      submitTypeEvaluationLatency =
          metricMaker.newTimer(
              "change/submit_type_evaluation",
              new Description("Latency for evaluating the submit type on a change.")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
    }
  }

  private final ProjectCache projectCache;
  private final PrologSubmitRuleUtil prologSubmitRuleUtil;
  private final SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory;
  private final Metrics metrics;

  @Inject
  private SubmitTypeEvaluator(
      ProjectCache projectCache,
      PrologSubmitRuleUtil prologSubmitRuleUtil,
      SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory,
      Metrics metrics) {
    this.projectCache = projectCache;
    this.prologSubmitRuleUtil = prologSubmitRuleUtil;
    this.queryBuilderFactory = queryBuilderFactory;
    this.metrics = metrics;
  }

  /**
   * Evaluate the submit type to be used.
   *
   * <p>The priority is as follows:
   *
   * <p>1) If the project has Prolog rules enabled, the submit type is determined by evaluating the
   * Prolog rules. 2) If submit type overrides are matching the change, the submit type is
   * determined by the highest priority matching override. 3) If neither of the above applies, the
   * submit type is determined by the project's submit type.
   *
   * @return record from the evaluated rules.
   * @param cd ChangeData to evaluate
   */
  public SubmitTypeRecord evaluate(ChangeData cd) {
    try (Timer0.Context ignored = metrics.submitTypeEvaluationLatency.start()) {
      if (cd.change() == null) {
        throw new StorageException("Change not found");
      }

      ProjectState projectState =
          projectCache
              .get(cd.project())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Unable to find project while evaluating submit rule",
                          new NoSuchProjectException(cd.project())));

      if (prologSubmitRuleUtil.isProjectRulesEnabled()) {
        return prologSubmitRuleUtil.getSubmitType(cd);
      }

      for (SubmitTypeOverride submitTypeOverride :
          projectState.getConfig().getSubmitTypeSections().values()) {

        try {
          if (isApplicable(submitTypeOverride.applicabilityExpression(), cd)) {
            return SubmitTypeRecord.OK(submitTypeOverride.type());
          }
        } catch (QueryParseException e) {
          return SubmitTypeRecord.error(
              "Failed to evaluate submit type override expression: " + e.getMessage());
        }
      }

      return SubmitTypeRecord.OK(projectState.getSubmitType());
    }
  }

  private boolean isApplicable(SubmitTypeOverrideExpression expression, ChangeData changeData)
      throws QueryParseException {
    Predicate<ChangeData> predicate =
        queryBuilderFactory
            .create(true) // For now always require an operator
            .parse(expression.expressionString());
    PredicateResult predicateResult = changeData.evaluatePredicateTree(predicate);
    return predicateResult.status();
  }
}
