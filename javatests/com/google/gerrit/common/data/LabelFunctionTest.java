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

package com.google.gerrit.common.data;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSet.Id;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import java.sql.Date;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LabelFunctionTest {
  private static final String LABEL_NAME = "Verified";
  private static final LabelId LABEL_ID = new LabelId(LABEL_NAME);
  private static final Change.Id CHANGE_ID = new Change.Id(100);
  private static final PatchSet.Id PS_ID = new PatchSet.Id(CHANGE_ID, 1);
  private static final LabelType VERIFIED_LABEL = makeLabel();
  private static final PatchSetApproval APPROVAL_2 = makeApproval((short) 2);
  private static final PatchSetApproval APPROVAL_1 = makeApproval((short) 1);
  private static final PatchSetApproval APPROVAL_0 = makeApproval((short) 0);
  private static final PatchSetApproval APPROVAL_M1 = makeApproval((short) -1);
  private static final PatchSetApproval APPROVAL_M2 = makeApproval((short) -2);

  private static LabelType makeLabel() {
    List<LabelValue> values = new ArrayList<>();
    values.add(new LabelValue((short) -2, "Great job, please fix compilation."));
    values.add(new LabelValue((short) -1, "Really good, please make some minor changes."));
    values.add(new LabelValue((short) 0, "No vote"));
    values.add(new LabelValue((short) 1, "Closest thing perfection"));
    values.add(new LabelValue((short) 2, "Perfect!"));
    return new LabelType(LABEL_NAME, values);
  }

  private static PatchSetApproval makeApproval(short value) {
    Account.Id accountId = new Account.Id(10000 + value);
    PatchSetApproval.Key key = makeKey(PS_ID, accountId, LABEL_ID);
    return new PatchSetApproval(key, value, Date.from(Instant.now()));
  }

  private static PatchSetApproval.Key makeKey(Id psId, Account.Id accountId, LabelId labelId) {
    return new PatchSetApproval.Key(psId, accountId, labelId);
  }

  private static void checkBlockWorks(LabelFunction function) {
    List<PatchSetApproval> approvals = Lists.newArrayList(APPROVAL_1, APPROVAL_M2, APPROVAL_2);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.REJECT);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_M2.getAccountId());
  }

  private static void checkNothingHappens(LabelFunction function) {
    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, Collections.emptyList());

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.MAY);
    assertThat(myLabel.appliedBy).isNull();
  }

  private static void checkLabelIsRequired(LabelFunction function) {
    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, Collections.emptyList());

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.NEED);
    assertThat(myLabel.appliedBy).isNull();
  }

  private static void checkMaxIsEnforced(LabelFunction function) {
    List<PatchSetApproval> approvals = Lists.newArrayList(APPROVAL_1, APPROVAL_0);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.NEED);
  }

  private void checkMaxValidatesTheLabel(LabelFunction function) {
    List<PatchSetApproval> approvals = Lists.newArrayList(APPROVAL_1, APPROVAL_2, APPROVAL_M1);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.OK);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_2.getAccountId());
  }

  @Test
  public void checkLabelNameIsCorrect() {
    for (LabelFunction function : LabelFunction.values()) {
      SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, Collections.emptyList());
      assertThat(myLabel.label).isEqualTo("Verified");
    }
  }

  @Test
  public void checkFunctionDoesNothing() {
    checkNothingHappens(LabelFunction.NO_BLOCK);
    checkNothingHappens(LabelFunction.NO_OP);
    checkNothingHappens(LabelFunction.PATCH_SET_LOCK);

    checkLabelIsRequired(LabelFunction.ANY_WITH_BLOCK);
    checkLabelIsRequired(LabelFunction.MAX_WITH_BLOCK);
    checkLabelIsRequired(LabelFunction.MAX_NO_BLOCK);
  }

  @Test
  public void checkBlockWorks() {
    checkBlockWorks(LabelFunction.ANY_WITH_BLOCK);
    checkBlockWorks(LabelFunction.MAX_WITH_BLOCK);
  }

  @Test
  public void checkMaxWorks() {
    checkMaxIsEnforced(LabelFunction.MAX_NO_BLOCK);
    checkMaxIsEnforced(LabelFunction.MAX_WITH_BLOCK);

    checkMaxValidatesTheLabel(LabelFunction.MAX_NO_BLOCK);
    checkMaxValidatesTheLabel(LabelFunction.MAX_WITH_BLOCK);
  }

  @Test
  public void checkMaxNoBlockIgnoresMin() {
    List<PatchSetApproval> approvals = Lists.newArrayList(APPROVAL_M2, APPROVAL_2, APPROVAL_M2);

    SubmitRecord.Label myLabel = LabelFunction.MAX_NO_BLOCK.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.OK);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_2.getAccountId());
  }
}
