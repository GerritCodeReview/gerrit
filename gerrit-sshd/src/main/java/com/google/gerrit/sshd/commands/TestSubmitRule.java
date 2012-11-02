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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.events.AccountAttribute;
import com.google.gerrit.server.events.SubmitLabelAttribute;
import com.google.gerrit.server.events.SubmitRecordAttribute;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.sshd.SshCommand;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.Term;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.LinkedList;
import java.util.List;

/** Command that allows testing of prolog submit-rules in a live instance. */
final class TestSubmitRule extends SshCommand {
  @Inject
  private ReviewDb db;

  @Inject
  private PrologEnvironment.Factory envFactory;

  @Inject
  private ChangeControl.Factory ccFactory;

  @Inject
  private AccountCache accountCache;

  final @AnonymousCowardName String anonymousCowardName;

  @Argument(index = 0, required = true, usage = "ChangeId to load in prolog environment")
  private String changeId;

  @Option(name = "-s",
      usage = "Read prolog script from stdin instead of reading rules.pl from the refs/meta/config branch")
  private boolean useStdin;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  private OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--no-filters", aliases = {"-n"},
      usage = "Don't run the submit_filter/2 from the parent projects")
  private boolean skipSubmitFilters;

  @Inject
  public TestSubmitRule(@AnonymousCowardName String anonymous) {
    anonymousCowardName = anonymous;
  }

  @Override
  protected void run() throws UnloggedFailure {
    try {
      List<Change> changeList =
          db.changes().byKey(new Change.Key(changeId)).toList();
      if (changeList.size() != 1)
        throw new UnloggedFailure(1, "Invalid ChangeId");

      Change c = changeList.get(0);
      PatchSet ps = db.patchSets().get(c.currentPatchSetId());
      // Will throw exception if current user can not access this change, and
      // thus will leak information that a change-id is valid even though the
      // user are not allowed to see the change.
      // See http://code.google.com/p/gerrit/issues/detail?id=1586
      ChangeControl cc = ccFactory.controlFor(c);

      SubmitRuleEvaluator evaluator = new SubmitRuleEvaluator(
          db, ps, cc.getProjectControl(), cc, c, null,
          false, "locate_submit_rule", "can_submit",
          "locate_submit_filter", "filter_submit_results");
      if (useStdin) {
        evaluator.readRulesFrom(in);
      }
      if (skipSubmitFilters) {
        evaluator.skipSubmitFilters();
      }
      @SuppressWarnings("unchecked")
      List<Term> results = evaluator.evaluate().toJava();

      List<SubmitRecord> res = cc.resultsToSubmitRecord(evaluator.getSubmitRule(),
          results);
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
    } catch (Exception e) {
      throw new UnloggedFailure("Processing of prolog script failed: " + e);
    }
  }
}
