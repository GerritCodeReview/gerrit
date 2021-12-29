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

package com.google.gerrit.server.approval;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.PatchSetApproval.UUID;
import com.google.inject.ImplementedBy;
import java.time.Instant;

/**
 * Generator for {@link PatchSetApproval.UUID}.
 *
 * <p>Since {@link PatchSetApproval.UUID} must be unique for each granted {@link PatchSetApproval},
 * implementations must generate globally unique UUID for each {@link #get} invocation.
 */
@ImplementedBy(PatchSetApprovalUuidGeneratorImpl.class)
public interface PatchSetApprovalUuidGenerator {

  /**
   * Generates {@link PatchSetApproval.UUID} based on the properties of {@link PatchSetApproval}
   * that is being granted.
   */
  UUID get(
      PatchSet.Id patchSetId, Account.Id accountId, String label, short value, Instant granted);
}
