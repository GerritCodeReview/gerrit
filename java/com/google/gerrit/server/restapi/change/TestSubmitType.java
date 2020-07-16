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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.MoreObjects;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.common.TestSubmitRuleInput.Filters;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.PrologOptions;
import com.google.gerrit.server.rules.PrologRule;
import com.google.gerrit.server.rules.RulesCache;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

public class TestSubmitType implements RestModifyView<RevisionResource, TestSubmitRuleInput> {
  private final ChangeData.Factory changeDataFactory;
  private final RulesCache rules;
  private final PrologRule prologRule;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitType(ChangeData.Factory changeDataFactory, RulesCache rules, PrologRule prologRule) {
    this.changeDataFactory = changeDataFactory;
    this.rules = rules;
    this.prologRule = prologRule;
  }

  @Override
  public Response<SubmitType> apply(RevisionResource rsrc, TestSubmitRuleInput input)
      throws AuthException, BadRequestException {
    if (input == null) {
      input = new TestSubmitRuleInput();
    }
    if (input.rule == null) {
      throw new BadRequestException("rule is required");
    }
    if (!rules.isProjectRulesEnabled()) {
      throw new AuthException("project rules are disabled");
    }
    input.filters = MoreObjects.firstNonNull(input.filters, filters);

    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    SubmitTypeRecord rec =
        prologRule.getSubmitType(
            cd, PrologOptions.dryRunOptions(input.rule, input.filters == Filters.SKIP));

    if (rec.status != SubmitTypeRecord.Status.OK) {
      throw new BadRequestException(String.format("rule produced invalid result: %s", rec));
    }

    return Response.ok(rec.type);
  }

  public static class Get implements RestReadView<RevisionResource> {
    private final ChangeData.Factory changeDataFactory;
    private final SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory;

    @Inject
    Get(
        ChangeData.Factory changeDataFactory,
        SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory) {
      this.changeDataFactory = changeDataFactory;
      this.submitRuleEvaluatorFactory = submitRuleEvaluatorFactory;
    }

    @Override
    public Response<SubmitType> apply(RevisionResource resource)
        throws AuthException, ResourceConflictException {
      SubmitRuleEvaluator evaluator =
          submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
      ChangeData cd = changeDataFactory.create(resource.getNotes());
      SubmitTypeRecord rec = evaluator.getSubmitType(cd);

      if (rec.status != SubmitTypeRecord.Status.OK) {
        throw new ResourceConflictException(String.format("rule produced invalid result: %s", rec));
      }

      return Response.ok(rec.type);
    }
  }
}
