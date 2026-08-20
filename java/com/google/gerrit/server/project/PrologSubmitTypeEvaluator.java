// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.PrologSubmitRuleUtil;
import com.google.inject.Inject;
import java.util.Optional;

public class PrologSubmitTypeEvaluator implements SubmitTypeEvaluator {

  private final PrologSubmitRuleUtil prologSubmitRuleUtil;

  @Inject
  private PrologSubmitTypeEvaluator(PrologSubmitRuleUtil prologSubmitRuleUtil) {
    this.prologSubmitRuleUtil = prologSubmitRuleUtil;
  }

  @Override
  public SubmitTypeRecord evaluate(ChangeData cd) {
    return evaluateRule(cd, null, false);
  }

  public SubmitTypeRecord evaluateRule(
      ChangeData cd, @Nullable String ruleToTest, boolean skipFilters) {
    Optional<SubmitTypeRecord> submitType = Optional.empty();
    if (ruleToTest != null) {
      submitType = prologSubmitRuleUtil.getSubmitType(cd, ruleToTest, skipFilters);
    } else {
      submitType = prologSubmitRuleUtil.getSubmitType(cd);
    }
    return submitType.orElseGet(SubmitTypeRecord::missing);
  }
}
