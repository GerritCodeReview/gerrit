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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.PredicateResult;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.entities.SubmitTypeRule;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder;
import com.google.inject.Inject;
import java.util.Set;

public class SubmitTypeRuleEvaluator implements SubmitTypeEvaluator {

  private final ProjectCache projectCache;
  private final SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory;

  @Inject
  private SubmitTypeRuleEvaluator(
      ProjectCache projectCache, SubmitRequirementChangeQueryBuilder.Factory queryBuilderFactory) {
    this.projectCache = projectCache;
    this.queryBuilderFactory = queryBuilderFactory;
  }

  @Override
  public SubmitTypeRecord evaluate(ChangeData cd) {
    ProjectState projectState = getProjectState(cd);
    ImmutableSet<SubmitTypeRule> overrides = ImmutableSet.copyOf(projectState.getSubmitTypeRules());
    return evaluateWithOverrides(cd, overrides);
  }

  public SubmitTypeRecord evaluateWithOverrides(ChangeData cd, Set<SubmitTypeRule> overrides) {
    for (SubmitTypeRule submitTypeOverride : overrides) {
      try {
        if (isApplicable(submitTypeOverride.applicabilityExpression(), cd)) {
          return SubmitTypeRecord.OK(submitTypeOverride.type());
        }
      } catch (QueryParseException e) {
        return SubmitTypeRecord.error(
            "Failed to evaluate submit type override expression: " + e.getMessage());
      }
    }
    return SubmitTypeRecord.missing();
  }

  private ProjectState getProjectState(ChangeData cd) {
    return projectCache
        .get(cd.project())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to find project while evaluating submit rule",
                    new NoSuchProjectException(cd.project())));
  }

  private boolean isApplicable(String expression, ChangeData changeData)
      throws QueryParseException {
    Predicate<ChangeData> predicate =
        queryBuilderFactory
            .create(true) // For now always require an operator
            .parse(expression);
    PredicateResult predicateResult = changeData.evaluatePredicateTree(predicate);
    return predicateResult.status();
  }
}
