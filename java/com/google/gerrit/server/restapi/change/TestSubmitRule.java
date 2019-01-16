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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInfo;
import com.google.gerrit.extensions.common.TestSubmitRuleInput;
import com.google.gerrit.extensions.common.TestSubmitRuleInput.Filters;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.DefaultSubmitRule;
import com.google.gerrit.server.rules.PrologRule;
import com.google.gerrit.server.rules.RulesCache;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.List;
import org.kohsuke.args4j.Option;

public class TestSubmitRule implements RestModifyView<RevisionResource, TestSubmitRuleInput> {
  private final ChangeData.Factory changeDataFactory;
  private final RulesCache rules;
  private final AccountLoader.Factory accountInfoFactory;
  private final ProjectCache projectCache;
  private final DefaultSubmitRule defaultSubmitRule;
  private final PrologRule prologRule;

  @Option(name = "--filters", usage = "impact of filters in parent projects")
  private Filters filters = Filters.RUN;

  @Inject
  TestSubmitRule(
      ChangeData.Factory changeDataFactory,
      RulesCache rules,
      AccountLoader.Factory infoFactory,
      ProjectCache projectCache,
      DefaultSubmitRule defaultSubmitRule,
      PrologRule prologRule) {
    this.changeDataFactory = changeDataFactory;
    this.rules = rules;
    this.accountInfoFactory = infoFactory;
    this.projectCache = projectCache;
    this.defaultSubmitRule = defaultSubmitRule;
    this.prologRule = prologRule;
  }

  @Override
  public List<TestSubmitRuleInfo> apply(RevisionResource rsrc, TestSubmitRuleInput input)
      throws AuthException, PermissionBackendException, BadRequestException {
    if (input == null) {
      input = new TestSubmitRuleInput();
    }
    if (input.rule != null && !rules.isProjectRulesEnabled()) {
      throw new AuthException("project rules are disabled");
    }
    input.filters = MoreObjects.firstNonNull(input.filters, filters);

    SubmitRuleOptions opts =
        SubmitRuleOptions.builder()
            .skipFilters(input.filters == Filters.SKIP)
            .rule(input.rule)
            .logErrors(false)
            .build();

    ProjectState projectState = projectCache.get(rsrc.getProject());
    if (projectState == null) {
      throw new BadRequestException("project not found");
    }
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    List<SubmitRecord> records;
    if (projectState.hasPrologRules() || input.rule != null) {
      records = ImmutableList.copyOf(prologRule.evaluate(cd, opts));
    } else {
      // No rules were provided as input and we have no rules.pl. This means we are supposed to run
      // the default rules. Nowadays, the default rules are implemented in Java, not Prolog.
      // Therefore, we call the DefaultRuleEvaluator instead.
      records = ImmutableList.copyOf(defaultSubmitRule.evaluate(cd, opts));
    }

    List<TestSubmitRuleInfo> out = Lists.newArrayListWithCapacity(records.size());
    AccountLoader accounts = accountInfoFactory.create(true);
    for (SubmitRecord r : records) {
      out.add(newSubmitRuleInfo(r, accounts));
    }
    accounts.fill();
    return out;
  }

  private static TestSubmitRuleInfo newSubmitRuleInfo(SubmitRecord r, AccountLoader accounts) {
    TestSubmitRuleInfo info = new TestSubmitRuleInfo();
    info.status = r.status.name();
    info.errorMessage = r.errorMessage;

    if (r.labels != null) {
      for (SubmitRecord.Label n : r.labels) {
        AccountInfo who = n.appliedBy != null ? accounts.get(n.appliedBy) : new AccountInfo(null);
        label(info, n, who);
      }
    }
    return info;
  }

  private static void label(TestSubmitRuleInfo info, SubmitRecord.Label n, AccountInfo who) {
    switch (n.status) {
      case OK:
        if (info.ok == null) {
          info.ok = new LinkedHashMap<>();
        }
        info.ok.put(n.label, who);
        break;
      case REJECT:
        if (info.reject == null) {
          info.reject = new LinkedHashMap<>();
        }
        info.reject.put(n.label, who);
        break;
      case NEED:
        if (info.need == null) {
          info.need = new LinkedHashMap<>();
        }
        info.need.put(n.label, TestSubmitRuleInfo.None.INSTANCE);
        break;
      case MAY:
        if (info.may == null) {
          info.may = new LinkedHashMap<>();
        }
        info.may.put(n.label, who);
        break;
      case IMPOSSIBLE:
        if (info.impossible == null) {
          info.impossible = new LinkedHashMap<>();
        }
        info.impossible.put(n.label, TestSubmitRuleInfo.None.INSTANCE);
        break;
    }
  }
}
