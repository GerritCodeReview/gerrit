// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.approval;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.server.approval.PatchSetApprovalUuidGenerator;
import com.google.gerrit.server.approval.PatchSetApprovalUuidGeneratorImpl;
import com.google.gerrit.server.util.time.TimeUtil;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link PatchSetApprovalUuidGeneratorImpl} - the default implementation of {@link
 * PatchSetApprovalUuidGenerator}.
 */
@RunWith(JUnit4.class)
public class PatchSetApprovalUuidTest {

  @Test
  public void sameInput_differentUuid() {
    PatchSetApprovalUuidGeneratorImpl patchSetApprovalUuidGenerator =
        new PatchSetApprovalUuidGeneratorImpl();
    for (short value = -2; value <= 2; value++) {
      PatchSet.Id patchSetId = PatchSet.id(Change.id(1, "project"), 1);
      Account.Id accountId = Account.id(1);
      String label = LabelId.CODE_REVIEW;
      Instant granted = TimeUtil.now();
      PatchSetApproval.UUID uuid1 =
          patchSetApprovalUuidGenerator.get(patchSetId, accountId, label, value, granted);
      PatchSetApproval.UUID uuid2 =
          patchSetApprovalUuidGenerator.get(patchSetId, accountId, label, value, granted);
      assertThat(uuid2).isNotEqualTo(uuid1);
    }
  }
}
