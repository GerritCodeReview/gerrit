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

package com.google.gerrit.server.change;

import com.google.common.base.Charsets;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.TestSubmitRule.Input;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.Term;

import java.io.ByteArrayInputStream;
import java.util.List;

class TestSubmitRule implements RestModifyView<RevisionResource, Input> {
  static class Input {
    @DefaultInput
    String rule;
    boolean runFilters = true;
  }

  private final ReviewDb db;

  @Inject
  TestSubmitRule(ReviewDb db) {
    this.db = db;
  }

  @Override
  public Object apply(RevisionResource rsrc, Input input) throws OrmException, BadRequestException {
    if (input == null) {
      input = new Input();
    }

    SubmitRuleEvaluator evaluator = new SubmitRuleEvaluator(
        db,
        rsrc.getPatchSet(),
        rsrc.getControl().getProjectControl(),
        rsrc.getControl(),
        rsrc.getChange(),
        new ChangeData(rsrc.getChange()),
        false,
        "locate_submit_rule", "can_submit",
        "locate_submit_filter", "filter_submit_results",
        !input.runFilters,
        input.rule != null
          ? new ByteArrayInputStream(input.rule.getBytes(Charsets.UTF_8))
          : null);

    List<Term> results;
    try {
      results = eval(evaluator);
    } catch (RuleEvalException e) {
      throw new BadRequestException(String.format(
          "rule failed with exception: %s",
          e.getMessage()));
    }
    if (results.isEmpty()) {
      throw new BadRequestException(String.format(
          "rule %s has no solutions",
          evaluator.getSubmitRule().toString()));
    }
    return rsrc.getControl().resultsToSubmitRecord(
        evaluator.getSubmitRule(),
        results);
  }

  @SuppressWarnings("unchecked")
  private static List<Term> eval(SubmitRuleEvaluator evaluator)
      throws RuleEvalException {
    return evaluator.evaluate().toJava();
  }
}
