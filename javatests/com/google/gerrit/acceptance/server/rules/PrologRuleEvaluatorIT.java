// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.rules;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.PrologOptions;
import com.google.gerrit.server.rules.PrologRuleEvaluator;
import com.google.gerrit.testing.TestChanges;
import com.google.inject.Inject;
import com.googlecode.prolog_cafe.lang.IntegerTerm;
import com.googlecode.prolog_cafe.lang.StructureTerm;
import com.googlecode.prolog_cafe.lang.Term;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class PrologRuleEvaluatorIT extends AbstractDaemonTest {
  @Inject private PrologRuleEvaluator.Factory evaluatorFactory;

  @Test
  public void convertsPrologToSubmitRecord() {
    PrologRuleEvaluator evaluator = makeEvaluator();

    StructureTerm verifiedLabel = makeLabel("Verified", "may");
    StructureTerm labels = new StructureTerm("label", verifiedLabel);

    List<Term> terms = ImmutableList.of(makeTerm("ok", labels));
    SubmitRecord record = evaluator.resultsToSubmitRecord(null, terms);

    assertThat(record.status).isEqualTo(SubmitRecord.Status.OK);
  }

  /**
   * The Prolog behavior is everything but intuitive. Several submit_rules can be defined, and each
   * will provide a different SubmitRecord answer when queried. The current implementation stops
   * parsing the Prolog terms into SubmitRecord objects once it finds an OK record. This might lead
   * to tangling results, as reproduced by this test.
   *
   * <p>Let's consider this rules.pl file (equivalent to the code in this test)
   *
   * <pre>{@code
   * submit_rule(submit(R)) :-
   *     gerrit:uploader(U),
   *     R = label('Verified', reject(U)).
   *
   * submit_rule(submit(CR, V)) :-
   *     gerrit:uploader(U),
   *     V = label('Code-Review', ok(U)).
   *
   * submit_rule(submit(R)) :-
   *     gerrit:uploader(U),
   *     R = label('Any-Label-Name', reject(U)).
   * }</pre>
   *
   * The first submit_rule always fails because the Verified label is rejected.
   *
   * <p>The second submit_rule is always valid, and provides two labels: OK and Code-Review.
   *
   * <p>The third submit_rule always fails because the Any-Label-Name label is rejected.
   *
   * <p>In this case, the last two SubmitRecords are used, the first one is discarded.
   */
  @Test
  public void abortsEarlyWithOkayRecord() {
    PrologRuleEvaluator evaluator = makeEvaluator();

    SubmitRecord.Label submitRecordLabel1 = new SubmitRecord.Label();
    submitRecordLabel1.label = "Verified";
    submitRecordLabel1.status = SubmitRecord.Label.Status.REJECT;
    submitRecordLabel1.appliedBy = admin.id();

    SubmitRecord.Label submitRecordLabel2 = new SubmitRecord.Label();
    submitRecordLabel2.label = "Code-Review";
    submitRecordLabel2.status = SubmitRecord.Label.Status.OK;
    submitRecordLabel2.appliedBy = admin.id();

    SubmitRecord.Label submitRecordLabel3 = new SubmitRecord.Label();
    submitRecordLabel3.label = "Any-Label-Name";
    submitRecordLabel3.status = SubmitRecord.Label.Status.REJECT;
    submitRecordLabel3.appliedBy = user.id();

    List<Term> terms = new ArrayList<>();

    StructureTerm label1 = makeLabel(submitRecordLabel1.label, "reject", admin);

    StructureTerm label2 = makeLabel(submitRecordLabel2.label, "ok", admin);

    StructureTerm label3 = makeLabel(submitRecordLabel3.label, "reject", user);

    terms.add(makeTerm("not_ready", makeLabels(label1)));
    terms.add(makeTerm("ok", makeLabels(label2)));
    terms.add(makeTerm("not_ready", makeLabels(label3)));

    // When
    SubmitRecord record = evaluator.resultsToSubmitRecord(null, terms);

    // assert that
    SubmitRecord expectedRecord = new SubmitRecord();
    expectedRecord.status = SubmitRecord.Status.OK;
    expectedRecord.labels = new ArrayList<>();
    expectedRecord.labels.add(submitRecordLabel2);
    expectedRecord.labels.add(submitRecordLabel3);

    assertThat(record).isEqualTo(expectedRecord);
  }

  private static Term makeTerm(String status, StructureTerm labels) {
    return new StructureTerm(status, labels);
  }

  private static StructureTerm makeLabel(String name, String status) {
    return new StructureTerm("label", new StructureTerm(name), new StructureTerm(status));
  }

  private static StructureTerm makeLabel(String name, String status, TestAccount account) {
    StructureTerm user = new StructureTerm("user", new IntegerTerm(account.id().get()));
    return new StructureTerm("label", new StructureTerm(name), new StructureTerm(status, user));
  }

  private static StructureTerm makeLabels(StructureTerm... labels) {
    return new StructureTerm("label", labels);
  }

  private ChangeData makeChangeData() {
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, admin.id()));
    return cd;
  }

  private PrologRuleEvaluator makeEvaluator() {
    return evaluatorFactory.create(makeChangeData(), PrologOptions.defaultOptions());
  }
}
