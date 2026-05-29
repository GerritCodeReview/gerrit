// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.collect.Streams;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.index.OnlineReindexMode;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.gerrit.server.rules.PrologSubmitRuleUtil;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current project and filters
 * the results through rules found in the parent projects, all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  public interface Factory {
    /** Returns a new {@link SubmitRuleEvaluator} with the specified options */
    SubmitRuleEvaluator create(SubmitRuleOptions options);
  }

  @Singleton
  private static class Metrics {
    final Timer0 submitRuleEvaluationLatency;
    final Timer0 submitTypeEvaluationLatency;

    @Inject
    Metrics(MetricMaker metricMaker) {
      submitRuleEvaluationLatency =
          metricMaker.newTimer(
              "change/submit_rule_evaluation",
              new Description("Latency for evaluating submit rules on a change.")
                  .setCumulative()
                  .setUnit(Units.MILLISECONDS));
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
  private final PluginSetContext<SubmitRule> submitRules;
  private final Metrics metrics;
  private final SubmitRuleOptions opts;

  @Inject
  private SubmitRuleEvaluator(
      ProjectCache projectCache,
      PrologSubmitRuleUtil prologSubmitRuleUtil,
      PluginSetContext<SubmitRule> submitRules,
      Metrics metrics,
      @Assisted SubmitRuleOptions options) {
    this.projectCache = projectCache;
    this.prologSubmitRuleUtil = prologSubmitRuleUtil;
    this.submitRules = submitRules;
    this.metrics = metrics;

    this.opts = options;
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated rules, including any
   *     errors.
   * @param cd ChangeData to evaluate
   */
  public List<SubmitRecord> evaluate(ChangeData cd) {
    try (TraceTimer timer =
            TraceContext.newTimer(
                "Evaluate submit rules",
                Metadata.builder().changeId(cd.change().getId().get()).build());
        Timer0.Context ignored = metrics.submitRuleEvaluationLatency.start()) {
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

      if (cd.change().isClosed()
          && (!opts.recomputeOnClosedChanges() || OnlineReindexMode.isActive())) {
        return cd.notes().getSubmitRecords().stream()
            .map(
                r -> {
                  SubmitRecord record = r.deepCopy();
                  if (record.status == SubmitRecord.Status.OK) {
                    // Submit records that were OK when they got merged are CLOSED now.
                    record.status = SubmitRecord.Status.CLOSED;
                  }
                  return record;
                })
            .collect(toImmutableList());
      }

      // We evaluate all the plugin-defined evaluators,
      // and then we collect the results in one list.
      return Streams.stream(submitRules)
          // Skip evaluating the default submit rule if the project has prolog rules.
          // Note that in this case, the prolog submit rule will handle labels for us
          .filter(
              projectState.hasPrologRules() && prologSubmitRuleUtil.isProjectRulesEnabled()
                  ? rule -> !(rule.get() instanceof DefaultSubmitRule)
                  : rule -> true)
          .map(
              c ->
                  c.call(
                      s -> {
                        Optional<SubmitRecord> record = s.evaluate(cd);
                        if (record.isPresent() && record.get().ruleName == null) {
                          // Only back-fill the ruleName if it was not populated by the "submit
                          // rule".
                          record.get().ruleName =
                              c.getPluginName() + "~" + s.getClass().getSimpleName();
                        }
                        return record;
                      }))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .collect(toImmutableList());
    }
  }

  /**
   * Evaluate the submit type rules to get the submit type.
   *
   * @return record from the evaluated rules.
   */
  public SubmitTypeRecord getSubmitType(ChangeData cd) {
    try (Timer0.Context ignored = metrics.submitTypeEvaluationLatency.start()) {
      ProjectState projectState =
          projectCache.get(cd.project()).orElseThrow(illegalState(cd.project()));
      if (!prologSubmitRuleUtil.isProjectRulesEnabled()) {
        return SubmitTypeRecord.OK(projectState.getSubmitType());
      }

      return prologSubmitRuleUtil.getSubmitType(cd);
    }
  }
}
