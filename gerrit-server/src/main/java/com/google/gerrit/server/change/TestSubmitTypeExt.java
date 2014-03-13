// Copyright (C) 2014 The Android Open Source Project
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
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.SubmitTypeExt;
import com.google.gerrit.reviewdb.client.SubmitTypeExt.ContentMerge;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.change.TestSubmitRule.Filters;
import com.google.gerrit.server.change.TestSubmitRule.Input;
import com.google.gerrit.server.project.RuleEvalException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.kohsuke.args4j.Option;

import java.io.ByteArrayInputStream;
import java.util.List;

public class TestSubmitTypeExt implements RestModifyView<RevisionResource, Input> {
  private final ReviewDb db;
  private final ChangeData.Factory changeDataFactory;
  private final RulesCache rules;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitTypeExt(ReviewDb db,
      ChangeData.Factory changeDataFactory,
      RulesCache rules) {
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.rules = rules;
  }

  @Override
  public SubmitTypeExt apply(RevisionResource rsrc, Input input)
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
    String typeName;
    ContentMerge contentMerge;
    if (type.isSymbol()) {
      typeName = ((SymbolTerm) type).name();
      contentMerge = ContentMerge.DEFAULT;
    } else if (type.isStructure()) {
      StructureTerm s = (StructureTerm) type;
      typeName = s.name();
      String n = s.arg(0).name();
      if (n.equals("content_merge")) {
        contentMerge = ContentMerge.TRUE;
      } else if (n.equals("no_content_merge")) {
        contentMerge = ContentMerge.FALSE;
      } else {
        throw new BadRequestException(String.format(
            "rule %s produced invalid result: %s",
            evaluator.getSubmitRule().toString(),
            type));
      }
    } else {
      throw new BadRequestException(String.format(
          "rule %s produced invalid result: %s",
          evaluator.getSubmitRule().toString(),
          type));
    }

    try {
      return new SubmitTypeExt(
          SubmitType.valueOf(typeName.toUpperCase()), contentMerge);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(String.format(
          "rule %s produced invalid result: %s",
          evaluator.getSubmitRule().toString(),
          type));
    }
  }

  static class Get implements RestReadView<RevisionResource> {
    private final TestSubmitTypeExt test;

    @Inject
    Get(TestSubmitTypeExt test) {
      this.test = test;
    }

    @Override
    public SubmitTypeExt apply(RevisionResource resource)
        throws AuthException, BadRequestException {
      return test.apply(resource, null);
    }
  }

}
