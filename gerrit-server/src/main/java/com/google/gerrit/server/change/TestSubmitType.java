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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Objects;
import com.google.gerrit.extensions.common.ProjectSubmitType;
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
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.kohsuke.args4j.Option;

import java.io.ByteArrayInputStream;
import java.util.List;

public class TestSubmitType implements RestModifyView<RevisionResource, Input> {
  private final ReviewDb db;
  private final ChangeData.Factory changeDataFactory;
  private final RulesCache rules;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitType(ReviewDb db,
      ChangeData.Factory changeDataFactory,
      RulesCache rules) {
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.rules = rules;
  }

  @Override
  public ProjectSubmitType apply(RevisionResource rsrc, Input input)
      throws AuthException, BadRequestException {
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
        changeDataFactory.create(db, rsrc.getChange()),
        false,
        "locate_submit_type", "get_submit_type",
        "locate_submit_type_filter", "filter_submit_type_results",
        input.filters == Filters.SKIP,
        input.rule != null
          ? new ByteArrayInputStream(input.rule.getBytes(UTF_8))
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

    String typeName = ((SymbolTerm) type).name();
    try {
      return ProjectSubmitType.valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format(
          "rule %s produced invalid result: %s",
          evaluator.getSubmitRule().toString(),
          type));
    }
  }

  static class Get implements RestReadView<RevisionResource> {
    private final TestSubmitType test;

    @Inject
    Get(TestSubmitType test) {
      this.test = test;
    }

    @Override
    public ProjectSubmitType apply(RevisionResource resource)
        throws AuthException, BadRequestException {
      return test.apply(resource, null);
    }
  }
}
