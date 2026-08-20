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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.SubmitTypeOverrideRule;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Set;

/** Evaluates which {@link SubmitType} is applicable for the given change. */
public class CombinedSubmitTypeEvaluator implements SubmitTypeEvaluator {

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

  private final PrologSubmitTypeEvaluator prologEvaluator;
  private final OverrideSubmitTypeEvaluator overrideEvaluator;
  private final ProjectDefaultSubmitTypeEvaluator defaultEvaluator;

  private final Metrics metrics;

  @Inject
  private CombinedSubmitTypeEvaluator(
      Metrics metrics,
      PrologSubmitTypeEvaluator prologEvaluator,
      OverrideSubmitTypeEvaluator overrideEvaluator,
      ProjectDefaultSubmitTypeEvaluator defaultEvaluator) {
    this.prologEvaluator = prologEvaluator;
    this.overrideEvaluator = overrideEvaluator;
    this.defaultEvaluator = defaultEvaluator;
    this.metrics = metrics;
  }

  /**
   * Evaluate the submit type to be used.
   *
   * <p>The priority is as follows:
   *
   * <ol>
   *   <li>If the project has a Prolog {@code submit_type/1} rule that explicitly matches the
   *       change, the submit type is determined by that rule.
   *   <li>If a submit type override's {@code applicableIf} expression matches the change, the
   *       submit type is determined by the first matching override.
   *   <li>Otherwise the project-wide default submit type is used.
   * </ol>
   *
   * <p>Note: if Prolog is enabled but the project's {@code rules.pl} file contains no explicit
   * {@code submit_type/1} clause (i.e. Prolog would fall back to {@code
   * project_default_submit_type}), step 2 is still evaluated so that submit type overrides can take
   * effect.
   *
   * @param cd ChangeData to evaluate
   * @return record from the evaluated rules.
   */
  @Override
  public SubmitTypeRecord evaluate(ChangeData cd) {
    try (Timer0.Context ignored = metrics.submitTypeEvaluationLatency.start()) {
      if (cd.change() == null) {
        throw new StorageException("Change not found");
      }

      SubmitTypeRecord rec = prologEvaluator.evaluate(cd);
      if (rec.isOk()) {
        return rec;
      }
      rec = overrideEvaluator.evaluate(cd);
      if (rec.isOk()) {
        return rec;
      }

      return defaultEvaluator.evaluate(cd);
    }
  }

  public SubmitTypeRecord testEvaluation(
      ChangeData cd,
      @Nullable String ruleToTest,
      boolean skipFilters,
      Set<SubmitTypeOverrideRule> overrides) {
    try (Timer0.Context ignored = metrics.submitTypeEvaluationLatency.start()) {
      if (cd.change() == null) {
        throw new StorageException("Change not found");
      }

      SubmitTypeRecord rec;
      if (ruleToTest != null) {
        rec = prologEvaluator.evaluateRule(cd, ruleToTest, skipFilters);
      } else {
        rec = prologEvaluator.evaluate(cd);
      }
      if (rec.isOk()) {
        return rec;
      }
      if (overrides.isEmpty()) {
        rec = overrideEvaluator.evaluate(cd);
      } else {
        rec = overrideEvaluator.evaluateWithOverrides(cd, overrides);
      }
      if (rec.isOk()) {
        return rec;
      }

      return defaultEvaluator.evaluate(cd);
    }
  }
}
