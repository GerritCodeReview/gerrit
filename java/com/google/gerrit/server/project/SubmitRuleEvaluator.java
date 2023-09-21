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

import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.index.OnlineReindexMode;
import com.google.gerrit.server.logging.CallerFinder;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.gerrit.server.rules.PrologRule;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.List;
import java.util.Optional;

/**
 * Evaluates a submit-like Prolog rule found in the rules.pl file of the current project and filters
 * the results through rules found in the parent projects, all the way up to All-Projects.
 */
public class SubmitRuleEvaluator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    /** Returns a new {@link SubmitRuleEvaluator} with the specified options */
    SubmitRuleEvaluator create(SubmitRuleOptions options);
  }

  private final ProjectCache projectCache;
  private final PrologRule prologRule;
  private final PluginSetContext<SubmitRule> submitRules;
  private final Timer0 submitRuleEvaluationLatency;
  private final Timer0 submitTypeEvaluationLatency;
  private final SubmitRuleOptions opts;
  private final CallerFinder callerFinder;

  @Inject
  private SubmitRuleEvaluator(
      ProjectCache projectCache,
      PrologRule prologRule,
      PluginSetContext<SubmitRule> submitRules,
      MetricMaker metricMaker,
      @Assisted SubmitRuleOptions options) {
    this.projectCache = projectCache;
    this.prologRule = prologRule;
    this.submitRules = submitRules;
    this.submitRuleEvaluationLatency =
        metricMaker.newTimer(
            "change/submit_rule_evaluation",
            new Description("Latency for evaluating submit rules on a change.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
    this.submitTypeEvaluationLatency =
        metricMaker.newTimer(
            "change/submit_type_evaluation",
            new Description("Latency for evaluating the submit type on a change.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));

    this.opts = options;

    this.callerFinder =
        CallerFinder.builder()
            .addTarget(ChangeApi.class)
            .addTarget(ChangeJson.class)
            .addTarget(ChangeData.class)
            .addTarget(SubmitRequirementsEvaluatorImpl.class)
            .build();
  }

  /**
   * Evaluate the submit rules.
   *
   * @return List of {@link SubmitRecord} objects returned from the evaluated rules, including any
   *     errors.
   * @param cd ChangeData to evaluate
   */
  public List<SubmitRecord> evaluate(ChangeData cd) {
    logger.atFine().log(
        "Evaluate submit rules for change %d (caller: %s)",
        cd.change().getId().get(), callerFinder.findCallerLazy());
    try (Timer0.Context ignored = submitRuleEvaluationLatency.start()) {
      Change change;
      ProjectState projectState;
      try {
        change = cd.change();
        if (change == null) {
          throw new StorageException("Change not found");
        }

        Project.NameKey name = cd.project();
        Optional<ProjectState> projectStateOptional = projectCache.get(name);
        if (!projectStateOptional.isPresent()) {
          throw new NoSuchProjectException(name);
        }
        projectState = projectStateOptional.get();
      } catch (NoSuchProjectException e) {
        throw new IllegalStateException("Unable to find project while evaluating submit rule", e);
      }

      if (change.isClosed() && (!opts.recomputeOnClosedChanges() || OnlineReindexMode.isActive())) {
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
              projectState.hasPrologRules()
                  ? rule -> !(rule.get() instanceof DefaultSubmitRule)
                  : rule -> true)
          .map(
              c ->
                  c.call(
                      s -> {
                        Optional<SubmitRecord> evaluate = s.evaluate(cd);
                        if (evaluate.isPresent()) {
                          evaluate.get().ruleName =
                              c.getPluginName() + "~" + s.getClass().getSimpleName();
                        }
                        return evaluate;
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
    try (Timer0.Context ignored = submitTypeEvaluationLatency.start()) {
      try {
        Project.NameKey name = cd.project();
        Optional<ProjectState> project = projectCache.get(name);
        if (!project.isPresent()) {
          throw new NoSuchProjectException(name);
        }
      } catch (NoSuchProjectException e) {
        throw new IllegalStateException("Unable to find project while evaluating submit rule", e);
      }

      return prologRule.getSubmitType(cd);
    }
  }
}
