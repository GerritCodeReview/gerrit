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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.TestSubmitType.Input;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Term;

import java.io.ByteArrayInputStream;

class TestSubmitType implements RestModifyView<RevisionResource, Input> {
  static class Input {
    @DefaultInput
    String rule;
    boolean runFilters = true;
  }

  private final ReviewDb db;

  @Inject
  TestSubmitType(ReviewDb db) {
    this.db = db;
  }

  @Override
  public String apply(RevisionResource rsrc, Input input) throws OrmException, BadRequestException {
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
        "locate_submit_type", "get_submit_type",
        "locate_submit_type_filter", "filter_submit_type_results",
        !input.runFilters,
        input.rule != null
          ? new ByteArrayInputStream(input.rule.getBytes(Charsets.UTF_8))
          : null);

    ListTerm results;
    try {
      results = evaluator.evaluate();
    } catch (RuleEvalException e) {
      throw new BadRequestException(String.format(
          "rule failed with exception: %s",
          e.getMessage()));
    }
    if (results.isNil()) {
      throw new BadRequestException(String.format(
          "rule %s has no solution",
          evaluator.getSubmitRule()));
    }
    Term type = results.car();
    if (!type.isSymbol()) {
      throw new BadRequestException(String.format(
          "rule %s produced invalid result: %s",
          evaluator.getSubmitRule().toString(),
          type));
    }
    return type.toString();
  }

  static class Get implements RestReadView<RevisionResource> {
    private final TestSubmitType test;

    @Inject
    Get(TestSubmitType test) {
      this.test = test;
    }

    @Override
    public String apply(RevisionResource resource) throws BadRequestException, OrmException {
      return test.apply(resource, null);
    }
  }
}
