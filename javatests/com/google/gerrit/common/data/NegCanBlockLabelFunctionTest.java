// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NegCanBlockLabelFunctionTest {
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

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          // No response -> MAY
          {ImmutableList.of(), SubmitRecord.Label.Status.MAY},
          // -2 at any point -> REJECT
          {ImmutableList.of(APPROVAL_M2), SubmitRecord.Label.Status.REJECT},
          {
            ImmutableList.of(APPROVAL_M2, APPROVAL_M1, APPROVAL_2), SubmitRecord.Label.Status.REJECT
          },
          {
            ImmutableList.of(APPROVAL_2, APPROVAL_M1, APPROVAL_M2), SubmitRecord.Label.Status.REJECT
          },
          {
            ImmutableList.of(APPROVAL_M1, APPROVAL_2, APPROVAL_M2), SubmitRecord.Label.Status.REJECT
          },
          {
            ImmutableList.of(APPROVAL_2, APPROVAL_M2, APPROVAL_M1), SubmitRecord.Label.Status.REJECT
          },
          // +2 approves and overrides a -1 (when no -2)
          {ImmutableList.of(APPROVAL_2), SubmitRecord.Label.Status.MAY},
          {ImmutableList.of(APPROVAL_M1, APPROVAL_2), SubmitRecord.Label.Status.MAY},
          {ImmutableList.of(APPROVAL_2, APPROVAL_M1), SubmitRecord.Label.Status.MAY},
          // -1 blocks when there is no +2 override
          {ImmutableList.of(APPROVAL_M1), SubmitRecord.Label.Status.REJECT},
          {ImmutableList.of(APPROVAL_M1, APPROVAL_1), SubmitRecord.Label.Status.REJECT},
          {ImmutableList.of(APPROVAL_1, APPROVAL_M1), SubmitRecord.Label.Status.REJECT},
        });
  }

  private List<PatchSetApproval> fApprovals;
  private SubmitRecord.Label.Status fExpected;

  public NegCanBlockLabelFunctionTest(
      List<PatchSetApproval> approvals, SubmitRecord.Label.Status expected) {
    this.fApprovals = approvals;
    this.fExpected = expected;
  }

  @Test
  public void test() {
    SubmitRecord.Label myLabel = LabelFunction.NEG_CAN_BLOCK.check(VERIFIED_LABEL, fApprovals);

    assertThat(myLabel.status).isEqualTo(fExpected);
  }

  protected static LabelType makeLabel() {
    List<LabelValue> values = new ArrayList<>();
    // The label text is irrelevant here, only the numerical value is used
    values.add(new LabelValue((short) -2, "Great job, please fix compilation."));
    values.add(new LabelValue((short) -1, "Really good, please make some minor changes."));
    values.add(new LabelValue((short) 0, "No vote."));
    values.add(new LabelValue((short) 1, "Closest thing perfection."));
    values.add(new LabelValue((short) 2, "Perfect!"));
    return new LabelType(LABEL_NAME, values);
  }

  protected static PatchSetApproval makeApproval(int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(PS_ID, Account.id(10000 + value), LABEL_ID))
        .value(value)
        .granted(Date.from(Instant.now()))
        .build();
  }
}
