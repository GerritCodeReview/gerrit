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

package com.google.gerrit.server.query.approval;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.notedb.ChangeNotes;

/** Entity representing all required information to match predicates for copying approvals. */
@AutoValue
public abstract class ApprovalContext {
  /** Approval on the source patch set to be copied. */
  public abstract PatchSetApproval patchSetApproval();

  /** Target change and patch set for the approval. */
  public abstract PatchSet.Id target();

  /** {@link ChangeNotes} of the change in question. */
  public abstract ChangeNotes changeNotes();

  /** {@link ChangeKind} of the delta between the origin and target patch set. */
  public abstract ChangeKind changeKind();

  public static ApprovalContext create(
      ChangeNotes changeNotes, PatchSetApproval psa, PatchSet.Id id, ChangeKind changeKind) {
    checkState(
        psa.patchSetId().changeId().equals(id.changeId()),
        "approval and target must be the same change. got: %s, %s",
        psa.patchSetId(),
        id);
    return new AutoValue_ApprovalContext(psa, id, changeNotes, changeKind);
  }
}
