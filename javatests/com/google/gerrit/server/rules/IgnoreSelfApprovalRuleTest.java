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
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.testing.GerritBaseTests;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import org.junit.Test;

public class IgnoreSelfApprovalRuleTest extends GerritBaseTests {
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
    values.add(new LabelValue((short) -2, "-2"));
    values.add(new LabelValue((short) -1, "-1"));
    values.add(new LabelValue((short) 0, "No vote."));
    values.add(new LabelValue((short) 1, "+1"));
    values.add(new LabelValue((short) 2, "+2"));
    return new LabelType(labelName, values);
  }

  private static PatchSetApproval makeApproval(LabelId labelId, Account.Id accountId, int value) {
    PatchSetApproval.Key key = makeKey(PS_ID, accountId, labelId);
    return new PatchSetApproval(key, (short) value, Date.from(Instant.now()));
  }

  private static PatchSetApproval.Key makeKey(
      PatchSet.Id psId, Account.Id accountId, LabelId labelId) {
    return PatchSetApproval.key(psId, accountId, labelId);
  }

  private static Account.Id makeAccount(int account) {
    return Account.id(account);
  }
}
