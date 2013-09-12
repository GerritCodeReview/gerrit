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
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.account.AccountInfo;
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
import java.util.Map;

public class TestSubmitRule implements RestModifyView<RevisionResource, Input> {
  public enum Filters {
    RUN, SKIP;
  }

  public static class Input {
    @DefaultInput
    public String rule;
    public Filters filters;
  }

  private final ReviewDb db;
  private final RulesCache rules;
  private final AccountInfo.Loader.Factory accountInfoFactory;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitRule(ReviewDb db, RulesCache rules,
      AccountInfo.Loader.Factory infoFactory) {
    this.db = db;
    this.rules = rules;
    this.accountInfoFactory = infoFactory;
  }

  @Override
  public Object apply(RevisionResource rsrc, Input input) throws OrmException,
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
        "locate_submit_rule", "can_submit",
        "locate_submit_filter", "filter_submit_results",
        input.filters == Filters.SKIP,
        input.rule != null
          ? new ByteArrayInputStream(input.rule.getBytes(Charsets.UTF_8))
          : null);

    List<Term> results;
    try {
      results = eval(evaluator);
    } catch (RuleEvalException e) {
      String msg = Joiner.on(": ").skipNulls().join(Iterables.transform(
          Throwables.getCausalChain(e),
          new Function<Throwable, String>() {
            @Override
            public String apply(Throwable in) {
              return in.getMessage();
            }
          }));
      throw new BadRequestException("rule failed: " + msg);
    }
    if (results.isEmpty()) {
      throw new BadRequestException(String.format(
          "rule %s has no solutions",
          evaluator.getSubmitRule().toString()));
    }

    List<SubmitRecord> records = rsrc.getControl().resultsToSubmitRecord(
        evaluator.getSubmitRule(),
        results);
    List<Record> out = Lists.newArrayListWithCapacity(records.size());
    AccountInfo.Loader accounts = accountInfoFactory.create(true);
    for (SubmitRecord r : records) {
      out.add(new Record(r, accounts));
    }
    accounts.fill();
    return out;
  }

  private static List<Term> eval(SubmitRuleEvaluator evaluator)
      throws RuleEvalException {
    return evaluator.evaluate();
  }

  static class Record {
    SubmitRecord.Status status;
    String errorMessage;
    Map<String, AccountInfo> ok;
    Map<String, AccountInfo> reject;
    Map<String, None> need;
    Map<String, AccountInfo> may;
    Map<String, None> impossible;

    Record(SubmitRecord r, AccountInfo.Loader accounts) {
      this.status = r.status;
      this.errorMessage = r.errorMessage;

      if (r.labels != null) {
        for (SubmitRecord.Label n : r.labels) {
          AccountInfo who = n.appliedBy != null
              ? accounts.get(n.appliedBy)
              : new AccountInfo(null);
          label(n, who);
        }
      }
    }

    private void label(SubmitRecord.Label n, AccountInfo who) {
      switch (n.status) {
        case OK:
          if (ok == null) {
            ok = Maps.newLinkedHashMap();
          }
          ok.put(n.label, who);
          break;
        case REJECT:
          if (reject == null) {
            reject = Maps.newLinkedHashMap();
          }
          reject.put(n.label, who);
          break;
        case NEED:
          if (need == null) {
            need = Maps.newLinkedHashMap();
          }
          need.put(n.label, new None());
          break;
        case MAY:
          if (may == null) {
            may = Maps.newLinkedHashMap();
          }
          may.put(n.label, who);
          break;
        case IMPOSSIBLE:
          if (impossible == null) {
            impossible = Maps.newLinkedHashMap();
          }
          impossible.put(n.label, new None());
          break;
      }
    }
  }

  static class None {
  }
}
