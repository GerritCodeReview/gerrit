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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.ListTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.List;

abstract class BaseTestSubmit extends SshCommand {
  @Inject
  protected ReviewDb db;

  @Inject
  private ChangeControl.Factory ccFactory;

  @Inject
  protected AccountCache accountCache;

  @Inject
  @AnonymousCowardName
  protected String anonymousCowardName;

  @Argument(index = 0, required = true, usage = "ChangeId to load in prolog environment")
  protected String changeId;

  @Option(name = "-s",
      usage = "Read prolog script from stdin instead of reading rules.pl from the refs/meta/config branch")
  protected boolean useStdin;

  @Option(name = "--format", metaVar = "FMT", usage = "Output display format")
  protected OutputFormat format = OutputFormat.TEXT;

  @Option(name = "--no-filters", aliases = {"-n"},
      usage = "Don't run the submit_filter/2 from the parent projects")
  protected boolean skipSubmitFilters;

  private Change change;

  private ChangeControl changeControl;

  protected abstract SubmitRuleEvaluator createEvaluator(PatchSet ps)
    throws Exception;
  protected abstract void processResults(ListTerm results, Term submitRule)
    throws Exception;

  protected final void run() throws UnloggedFailure {
    try {
      PatchSet ps = db.patchSets().get(getChange().currentPatchSetId());
      SubmitRuleEvaluator evaluator = createEvaluator(ps);
      processResults(evaluator.evaluate(), evaluator.getSubmitRule());
    } catch (Exception e) {
      throw new UnloggedFailure("Processing of prolog script failed: " + e);
    }
  }

  protected final Change getChange() throws OrmException, UnloggedFailure {
    if (change == null) {
      List<Change> changeList =
          db.changes().byKey(new Change.Key(changeId)).toList();
      if (changeList.size() != 1)
        throw new UnloggedFailure(1, "Invalid ChangeId");

      change = changeList.get(0);
    }
    return change;
  }

  protected final ChangeControl getChangeControl() throws OrmException,
      NoSuchChangeException, UnloggedFailure {
    if (changeControl == null) {
      // Will throw exception if current user can not access this change, and
      // thus will leak information that a change-id is valid even though the
      // user are not allowed to see the change.
      // See http://code.google.com/p/gerrit/issues/detail?id=1586
      changeControl = ccFactory.controlFor(getChange());
    }
    return changeControl;
  }
}
