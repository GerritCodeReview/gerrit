// Copyright (C) 2013 The Android Open Source Project
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
import com.google.common.base.Objects;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.change.TestSubmitRule.Filters;
import com.google.gerrit.server.change.TestSubmitRule.Input;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.Term;

import org.kohsuke.args4j.Option;

import java.io.ByteArrayInputStream;
import java.util.List;

public class TestSubmitType implements RestModifyView<RevisionResource, Input> {
  private final ReviewDb db;
  private final RulesCache rules;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitType(ReviewDb db, RulesCache rules) {
    this.db = db;
    this.rules = rules;
  }

  @Override
  public String apply(RevisionResource rsrc, Input input) throws OrmException,
      BadRequestException, AuthException {
    if (input == null) {
      input = new Input();
    }
    if (input.rule != null && !rules.isProjectRulesEnabled()) {
      throw new AuthException("project rules are disabled");
    }
    input.filters = Objects.firstNonNull(input.filters, filters);

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
        input.filters == Filters.SKIP,
        input.rule != null
          ? new ByteArrayInputStream(input.rule.getBytes(Charsets.UTF_8))
          : null);

    List<Term> results;
    try {
      results = evaluator.evaluate();
    } catch (RuleEvalException e) {
      throw new BadRequestException(String.format(
          "rule failed with exception: %s",
          e.getMessage()));
    }
    if (results.isEmpty()) {
      throw new BadRequestException(String.format(
          "rule %s has no solution",
          evaluator.getSubmitRule()));
    }
    Term type = results.get(0);
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
    public String apply(RevisionResource resource) throws BadRequestException,
        OrmException, AuthException {
      return test.apply(resource, null);
    }
  }
}
