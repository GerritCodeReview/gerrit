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

package com.google.gerrit.server.rules;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.junit.Test;

public class IgnoreSelfApprovalRuleTest {
  private static final Change.Id CHANGE_ID = Change.id(100);
  private static final PatchSet.Id PS_ID = PatchSet.id(CHANGE_ID, 1);
  private static final LabelType VERIFIED = makeLabel("Verified");
  private static final Account.Id USER1 = makeAccount(100001);

  @Test
  public void filtersByLabel() {
    LabelType codeReview = makeLabel("Code-Review");
    PatchSetApproval approvalVerified = makeApproval(VERIFIED.getLabelId(), USER1, 2);
    PatchSetApproval approvalCr = makeApproval(codeReview.getLabelId(), USER1, 2);

    Collection<PatchSetApproval> filteredApprovals =
        IgnoreSelfApprovalRule.filterApprovalsByLabel(
            ImmutableList.of(approvalVerified, approvalCr), VERIFIED);

    assertThat(filteredApprovals).containsExactly(approvalVerified);
  }

  @Test
  public void filtersVotesFromUser() {
    PatchSetApproval approvalM2 = makeApproval(VERIFIED.getLabelId(), USER1, -2);
    PatchSetApproval approvalM1 = makeApproval(VERIFIED.getLabelId(), USER1, -1);

    ImmutableList<PatchSetApproval> approvals =
        ImmutableList.of(
            approvalM2,
            approvalM1,
            makeApproval(VERIFIED.getLabelId(), USER1, 0),
            makeApproval(VERIFIED.getLabelId(), USER1, +1),
            makeApproval(VERIFIED.getLabelId(), USER1, +2));

    Collection<PatchSetApproval> filteredApprovals =
        IgnoreSelfApprovalRule.filterOutPositiveApprovalsOfUser(approvals, USER1);

    assertThat(filteredApprovals).containsExactly(approvalM1, approvalM2);
  }

  private static LabelType makeLabel(String labelName) {
    List<LabelValue> values = new ArrayList<>();
    // The label text is irrelevant here, only the numerical value is used
    values.add(LabelValue.create((short) -2, "-2"));
    values.add(LabelValue.create((short) -1, "-1"));
    values.add(LabelValue.create((short) 0, "No vote."));
    values.add(LabelValue.create((short) 1, "+1"));
    values.add(LabelValue.create((short) 2, "+2"));
    return LabelType.create(labelName, values);
  }

  private static PatchSetApproval makeApproval(LabelId labelId, Account.Id accountId, int value) {
    return PatchSetApproval.builder()
        .key(PatchSetApproval.key(PS_ID, accountId, labelId))
        .value(value)
        .granted(Date.from(Instant.now()))
        .build();
  }

  private static Account.Id makeAccount(int account) {
    return Account.id(account);
  }
}
