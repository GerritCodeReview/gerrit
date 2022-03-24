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

package com.google.gerrit.server.approval.testing;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApproval.UUID;
import com.google.gerrit.server.approval.PatchSetApprovalUuidGenerator;
import java.time.Instant;
import javax.inject.Singleton;

/**
 * Implementation of {@link PatchSetApprovalUuidGenerator} that returns predictable {@link UUID}.
 */
@Singleton
public class TestPatchSetApprovalUuidGenerator implements PatchSetApprovalUuidGenerator {

  private int invocationCount = 0;

  @Override
  public UUID get(
      PatchSet.Id patchSetId, Account.Id accountId, String label, short value, Instant granted) {
    invocationCount++;
    return PatchSetApproval.uuid(
        String.format(
                "%s_%s_%s_%s_%s_%s",
                patchSetId.changeId().get(),
                patchSetId.get(),
                accountId.get(),
                label,
                value,
                invocationCount)
            .replace("-", "_")
            .toLowerCase());
  }
}
