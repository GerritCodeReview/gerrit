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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.RepoView;

/** Entity representing all required information to match predicates for copying approvals. */
@AutoValue
public abstract class ApprovalContext {
  public abstract PatchSet.Id sourcePatchSetId();

  public abstract Account.Id approverId();

  public abstract LabelType labelType();

  /** Value of the approval on the source patch set to be copied. */
  public abstract short approvalValue();

  /**
   * Target change and patch set for the approval. This must be used instead of getting the PatchSet
   * from {@link #changeNotes()} because it is possible we are now creating the patch-set, so it
   * doesn't exist in changeNotes yet.
   */
  public abstract PatchSet targetPatchSet();

  /** {@link ChangeNotes} of the change in question. */
  public abstract ChangeNotes changeNotes();

  /** {@link ChangeData } of the change in question. */
  public abstract ChangeData changeData();

  /** {@link ChangeKind} of the delta between the origin and target patch set. */
  public abstract ChangeKind changeKind();

  /** Whether the new patch set is a merge commit. */
  public abstract boolean isMerge();

  public abstract RepoView repoView();

  public static ApprovalContext create(
      ChangeData changeData,
      PatchSet.Id sourcePatchSetId,
      Account.Id approverId,
      LabelType labelType,
      short approvalValue,
      PatchSet targetPatchSet,
      ChangeKind changeKind,
      boolean isMerge,
      RepoView repoView) {
    checkState(
        sourcePatchSetId.changeId().equals(targetPatchSet.id().changeId()),
        "approval and target must be the same change. got: %s, %s",
        sourcePatchSetId,
        targetPatchSet.id());
    // TODO(ekempin): Use checkState to verify that psa.patchSetId().get() + 1 == id.get() so that
    // it's ensured that approvals are only copied to the next consecutive patch set. To add back
    // this verification https://gerrit-review.googlesource.com/c/gerrit/+/312633 can be reverted.
    // As explained in the commit message of this change doing this check is only possible if there
    // are no changes with gaps in patch set numbers. Since it's planned to fix-up old changes with
    // gaps in patch set numbers, this todo is a reminder to add back the check once this is done.
    return new AutoValue_ApprovalContext(
        sourcePatchSetId,
        approverId,
        labelType,
        approvalValue,
        targetPatchSet,
        changeData.notes(),
        changeData,
        changeKind,
        isMerge,
        repoView);
  }
}
