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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.events.SubmitLabelAttribute;
import com.google.gerrit.server.events.SubmitRecordAttribute;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gson.reflect.TypeToken;

import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Term;

import java.util.LinkedList;
import java.util.List;

/** Command that allows testing of prolog submit-rules in a live instance. */
@CommandMetaData(name = "rule", descr = "Test prolog submit rules")
final class TestSubmitRule extends BaseTestSubmit {

  protected SubmitRuleEvaluator createEvaluator(PatchSet ps) throws Exception {
    ChangeControl cc = getChangeControl();
    return new SubmitRuleEvaluator(
        db, ps, cc.getProjectControl(), cc, getChange(), null,
        false, "locate_submit_rule", "can_submit",
        "locate_submit_filter", "filter_submit_results",
        skipSubmitFilters, useStdin ? in : null);
  }

  protected void processResults(ListTerm results, Term submitRule) throws Exception {
    @SuppressWarnings("unchecked")
    List<SubmitRecord> res = getChangeControl().resultsToSubmitRecord(submitRule,
        results.toJava());
    if (res.isEmpty()) {
      // Should never occur for a well written rule
      Change c = getChange();
      stderr.print("Submit rule " + submitRule + " for change " + c.getChangeId()
          + " of " + c.getProject().get() + " has no solution");
      return;
    }
    for (SubmitRecord r : res) {
      if (format.isJson()) {
        SubmitRecordAttribute submitRecord = new SubmitRecordAttribute();
        submitRecord.status = r.status.name();

        List<SubmitLabelAttribute> submitLabels = new LinkedList<SubmitLabelAttribute>();
        for(SubmitRecord.Label l : r.labels) {
          SubmitLabelAttribute label = new SubmitLabelAttribute();
          label.label = l.label;
          label.status= l.status.name();
          if(l.appliedBy != null) {
            Account a = accountCache.get(l.appliedBy).getAccount();
            label.by = new AccountAttribute();
            label.by.email = a.getPreferredEmail();
            label.by.name = a.getFullName();
            label.by.username = a.getUserName();
          }
          submitLabels.add(label);
        }
        submitRecord.labels = submitLabels;
        format.newGson().toJson(submitRecord, new TypeToken<SubmitRecordAttribute>() {}.getType(), stdout);
        stdout.print('\n');
      } else {
        for(SubmitRecord.Label l : r.labels) {
          stdout.print(l.label + ": " + l.status);
          if(l.appliedBy != null) {
            AccountInfo a = new AccountInfo(accountCache.get(l.appliedBy).getAccount());
            stdout.print(" by " + a.getNameEmail(anonymousCowardName));
          }
          stdout.print('\n');
        }
        stdout.print("\n" + r.status.name() + "\n");
      }
    }
  }
}
