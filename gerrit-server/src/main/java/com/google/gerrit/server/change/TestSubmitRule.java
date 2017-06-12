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

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.common.TestSubmitRuleInput.Filters;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.RulesCache;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Option;

public class TestSubmitRule implements RestModifyView<RevisionResource, TestSubmitRuleInput> {
  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final RulesCache rules;
  private final AccountLoader.Factory accountInfoFactory;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitRule(
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      RulesCache rules,
      AccountLoader.Factory infoFactory) {
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.rules = rules;
    this.accountInfoFactory = infoFactory;
  }

  @Override
  public List<Record> apply(RevisionResource rsrc, TestSubmitRuleInput input)
      throws AuthException, OrmException {
    if (input == null) {
      input = new TestSubmitRuleInput();
    }
    if (input.rule != null && !rules.isProjectRulesEnabled()) {
      throw new AuthException("project rules are disabled");
    }
    input.filters = MoreObjects.firstNonNull(input.filters, filters);
    SubmitRuleEvaluator evaluator =
        new SubmitRuleEvaluator(changeDataFactory.create(db.get(), rsrc.getControl()));

    List<SubmitRecord> records =
        evaluator
            .setPatchSet(rsrc.getPatchSet())
            .setLogErrors(false)
            .setSkipSubmitFilters(input.filters == Filters.SKIP)
            .setRule(input.rule)
            .evaluate();
    List<Record> out = Lists.newArrayListWithCapacity(records.size());
    AccountLoader accounts = accountInfoFactory.create(true);
    for (SubmitRecord r : records) {
      out.add(new Record(r, accounts));
    }
    if (!out.isEmpty()) {
      out.get(0).prologReductionCount = evaluator.getReductionsConsumed();
    }
    accounts.fill();
    return out;
  }

  static class Record {
    SubmitRecord.Status status;
    String errorMessage;
    Map<String, AccountInfo> ok;
    Map<String, AccountInfo> reject;
    Map<String, None> need;
    Map<String, AccountInfo> may;
    Map<String, None> impossible;
    Long prologReductionCount;

    Record(SubmitRecord r, AccountLoader accounts) {
      this.status = r.status;
      this.errorMessage = r.errorMessage;

      if (r.labels != null) {
        for (SubmitRecord.Label n : r.labels) {
          AccountInfo who = n.appliedBy != null ? accounts.get(n.appliedBy) : new AccountInfo(null);
          label(n, who);
        }
      }
    }

    private void label(SubmitRecord.Label n, AccountInfo who) {
      switch (n.status) {
        case OK:
          if (ok == null) {
            ok = new LinkedHashMap<>();
          }
          ok.put(n.label, who);
          break;
        case REJECT:
          if (reject == null) {
            reject = new LinkedHashMap<>();
          }
          reject.put(n.label, who);
          break;
        case NEED:
          if (need == null) {
            need = new LinkedHashMap<>();
          }
          need.put(n.label, new None());
          break;
        case MAY:
          if (may == null) {
            may = new LinkedHashMap<>();
          }
          may.put(n.label, who);
          break;
        case IMPOSSIBLE:
          if (impossible == null) {
            impossible = new LinkedHashMap<>();
          }
          impossible.put(n.label, new None());
          break;
      }
    }
  }

  static class None {}
}
