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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.Test;

public class LabelFunctionTest {
  private static final String LABEL_NAME = "Verified";
  private static final LabelId LABEL_ID = LabelId.create(LABEL_NAME);
  private static final Change.Id CHANGE_ID = Change.id(100);
  private static final PatchSet.Id PS_ID = PatchSet.id(CHANGE_ID, 1);
  private static final LabelType VERIFIED_LABEL = makeLabel();
  private static final PatchSetApproval APPROVAL_2 = makeApproval(2);
  private static final PatchSetApproval APPROVAL_1 = makeApproval(1);
  private static final PatchSetApproval APPROVAL_0 = makeApproval(0);
  private static final PatchSetApproval APPROVAL_M1 = makeApproval(-1);
  private static final PatchSetApproval APPROVAL_M2 = makeApproval(-2);

  @Test
  public void checkLabelNameIsCorrect() {
    for (LabelFunction function : LabelFunction.values()) {
      SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, ImmutableList.of());
      assertThat(myLabel.label).isEqualTo("Verified");
    }
  }

  @Test
  public void checkFunctionDoesNothing() {
    checkNothingHappens(LabelFunction.NO_BLOCK);
    checkNothingHappens(LabelFunction.NO_OP);
    checkNothingHappens(LabelFunction.PATCH_SET_LOCK);
    checkNothingHappens(LabelFunction.ANY_WITH_BLOCK);

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
    List<PatchSetApproval> approvals = ImmutableList.of(APPROVAL_M2, APPROVAL_2, APPROVAL_M2);

    SubmitRecord.Label myLabel = LabelFunction.MAX_NO_BLOCK.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.OK);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_2.accountId());
  }

  private static LabelType makeLabel() {
    List<LabelValue> values = new ArrayList<>();
    // The label text is irrelevant here, only the numerical value is used
    values.add(LabelValue.create((short) -2, "Great job, please fix compilation."));
    values.add(LabelValue.create((short) -1, "Really good, please make some minor changes."));
    values.add(LabelValue.create((short) 0, "No vote."));
    values.add(LabelValue.create((short) 1, "Closest thing perfection."));
    values.add(LabelValue.create((short) 2, "Perfect!"));
    return LabelType.create(LABEL_NAME, values);
  }

  private static PatchSetApproval makeApproval(int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(PS_ID, Account.id(10000 + value), LABEL_ID))
        .value(value)
        .granted(Date.from(Instant.now()))
        .build();
  }

  private static void checkBlockWorks(LabelFunction function) {
    List<PatchSetApproval> approvals = ImmutableList.of(APPROVAL_1, APPROVAL_M2, APPROVAL_2);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.REJECT);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_M2.accountId());
  }

  private static void checkNothingHappens(LabelFunction function) {
    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, ImmutableList.of());

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.MAY);
    assertThat(myLabel.appliedBy).isNull();
  }

  private static void checkLabelIsRequired(LabelFunction function) {
    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, ImmutableList.of());

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.NEED);
    assertThat(myLabel.appliedBy).isNull();
  }

  private static void checkMaxIsEnforced(LabelFunction function) {
    List<PatchSetApproval> approvals = ImmutableList.of(APPROVAL_1, APPROVAL_0);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.NEED);
  }

  private static void checkMaxValidatesTheLabel(LabelFunction function) {
    List<PatchSetApproval> approvals = ImmutableList.of(APPROVAL_1, APPROVAL_2, APPROVAL_M1);

    SubmitRecord.Label myLabel = function.check(VERIFIED_LABEL, approvals);

    assertThat(myLabel.status).isEqualTo(SubmitRecord.Label.Status.OK);
    assertThat(myLabel.appliedBy).isEqualTo(APPROVAL_2.accountId());
  }
}
