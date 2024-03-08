// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.rules.prolog;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.PrologSubmitRuleUtil;
import com.google.inject.Singleton;
import javax.inject.Inject;

/** Implementation of {@link PrologSubmitRuleUtil}. */
@Singleton
public class PrologSubmitRuleUtilImpl implements PrologSubmitRuleUtil {
  private final PrologRule prologRule;
  private final RulesCache rulesCache;

  @Inject
  public PrologSubmitRuleUtilImpl(PrologRule prologRule, RulesCache rulesCache) {
    this.prologRule = prologRule;
    this.rulesCache = rulesCache;
  }

  @Override
  public boolean isProjectRulesEnabled() {
    return rulesCache.isProjectRulesEnabled();
  }

  @Override
  public SubmitTypeRecord getSubmitType(ChangeData cd) {
    checkState(isProjectRulesEnabled(), "prolog rules disabled");
    return prologRule.getSubmitType(cd, PrologOptions.defaultOptions());
  }

  @Override
  public SubmitTypeRecord getSubmitType(ChangeData cd, String ruleToTest, boolean skipFilters) {
    checkState(isProjectRulesEnabled(), "prolog rules disabled");
    return prologRule.getSubmitType(cd, PrologOptions.dryRunOptions(ruleToTest, skipFilters));
  }

  @Override
  public SubmitRecord evaluate(ChangeData cd, String ruleToTest, boolean skipFilters) {
    checkState(isProjectRulesEnabled(), "prolog rules disabled");
    return prologRule.evaluate(cd, PrologOptions.dryRunOptions(ruleToTest, skipFilters));
  }
}
