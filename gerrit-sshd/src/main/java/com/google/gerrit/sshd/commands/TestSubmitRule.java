// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologEnvironment;
import com.google.gerrit.rules.StoredValues;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.JavaObjectTerm;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.PrologMachineCopy;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;
import com.googlecode.prolog_cafe.lang.VariableTerm;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.List;

/* Command that allows (easier) testing of prolog submit-rules
 *
 * If the rule in question uses current_user/1(2),
 * you can impersonate the user using "suexec".
 */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
final class TestSubmitRule extends SshCommand {
  @Inject
  private ReviewDb db;

  @Inject
  private PrologEnvironment.Factory envFactory;

  @Inject
  private ChangeControl.Factory ccFactory;

  @Option(name = "-c", required = true, usage = "ChangeId to load in prolog environment.")
  private String changeId;

  private static final String[] PACKAGE_LIST = {
    Prolog.BUILTIN,
    "gerrit",
  };
  private PrologMachineCopy newMachine() {
    BufferingPrologControl ctl = new BufferingPrologControl();
    ctl.setMaxDatabaseSize(16 * 1024);
    ctl.setPrologClassLoader(new PrologClassLoader(getClass().getClassLoader()));
    return PrologMachineCopy.save(ctl);
  }

  @Override
  protected void run() throws UnloggedFailure {
    PushbackReader inReader = new PushbackReader(new InputStreamReader(in));
    PrintWriter outWriter = new PrintWriter(new OutputStreamWriter(out));

    try {
      PrologEnvironment pcl = envFactory.create(newMachine());
      List<Change> changeList = db.changes().byKey(new Change.Key(changeId)).toList();
      if(changeList.size() == 0)
        throw new UnloggedFailure(1,"Invalid ChangeId");

      Change c = changeList.get(0);
      PatchSet ps = db.patchSets().get(c.currentPatchSetId());
      ChangeControl cc = ccFactory.controlFor(c);

      pcl.set(StoredValues.REVIEW_DB, db);
      pcl.set(StoredValues.CHANGE, c);
      pcl.set(StoredValues.PATCH_SET, ps);
      pcl.set(StoredValues.CHANGE_CONTROL, cc);
      pcl.initialize(PACKAGE_LIST);


      pcl.execute(
          Prolog.BUILTIN, "consult_stream",
          SymbolTerm.intern("rules.pl"),
          new JavaObjectTerm(inReader));
      List<Term> results = new ArrayList<Term>();
      Term submitRule = pcl.once("gerrit", "locate_submit_rule", new VariableTerm());

      for (Term[] template : pcl.all("gerrit", "can_submit",
          submitRule, new VariableTerm())) {
        results.add(template[1]);
      }
      List<SubmitRecord> res = cc.resultsToSubmitRecord(submitRule,results);
      for (SubmitRecord r : res) {
        outWriter.println("Result: " + r);
      }
    } catch (Exception e) {
      throw new UnloggedFailure("Processing of prolog script failed: " + e);
    }
    try {
      outWriter.flush();
      outWriter.close();
      inReader.close();
    } catch (IOException e) {
      e.printStackTrace();
    }


  }
}
