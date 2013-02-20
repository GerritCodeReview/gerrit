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
//

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.sshd.CommandMetaData;

import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Term;

import java.util.List;

@CommandMetaData(name = "type", descr = "Test prolog submit type")
final class TestSubmitType extends BaseTestSubmit {

  @Override
  protected SubmitRuleEvaluator createEvaluator(PatchSet ps) throws Exception {
    ChangeControl cc = getChangeControl();
    return new SubmitRuleEvaluator(
        db, ps, cc.getProjectControl(), cc, getChange(), null,
        false, "locate_submit_type", "get_submit_type",
        "locate_submit_type_filter", "filter_submit_type_results",
        skipSubmitFilters, useStdin ? in : null);
  }

  @Override
  protected void processResults(ListTerm resultsTerm, Term submitRule)
      throws Exception {
    @SuppressWarnings("unchecked")
    List<String> results = resultsTerm.toJava();
    if (results.isEmpty()) {
      // Should never occur for a well written rule
      Change c = getChange();
      stderr.print("Submit rule " + submitRule + " for change " + c.getChangeId()
          + " of " + c.getProject().get() + " has no solution");
      return;
    }
    String typeName = results.get(0);
    stdout.print(typeName);
    stdout.print('\n');
  }
}
