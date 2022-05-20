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
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.revwalk.RevWalk;

/** Entity representing all required information to match predicates for copying approvals. */
@AutoValue
public abstract class ApprovalContext {
  /** Key of the approval on the source patch set to be copied. */
  public abstract PatchSetApproval.Key patchSetApprovalKey();

  /** Value of the approval on the source patch set to be copied. */
  public abstract short patchSetApprovalValue();

  /**
   * Target change and patch set for the approval. This must be used instead of getting the PatchSet
   * from {@link #changeNotes()} because it is possible we are now creating the patch-set, so it
   * doesn't exist in changeNotes yet.
   */
  public abstract PatchSet target();

  /** {@link ChangeNotes} of the change in question. */
  public abstract ChangeNotes changeNotes();

  /** {@link ChangeKind} of the delta between the origin and target patch set. */
  public abstract ChangeKind changeKind();

  /** Whether the new patch set is a merge commit. */
  public abstract boolean isMerge();

  /** {@link RevWalk} of the repository for the current commit. */
  public abstract RevWalk revWalk();

  /** {@link RevWalk} of the repository for the current commit. */
  public abstract Config repoConfig();

  public static ApprovalContext create(
      ChangeNotes changeNotes,
      PatchSetApproval.Key psaKey,
      short psaValue,
      PatchSet patchSet,
      ChangeKind changeKind,
      boolean isMerge,
      RevWalk revWalk,
      Config repoConfig) {
    checkState(
        psaKey.patchSetId().changeId().equals(patchSet.id().changeId()),
        "approval and target must be the same change. got: %s, %s",
        psaKey.patchSetId(),
        patchSet.id());
    // TODO(ekempin): Use checkState to verify that psa.patchSetId().get() + 1 == id.get() so that
    // it's ensured that approvals are only copied to the next consecutive patch set. To add back
    // this verification https://gerrit-review.googlesource.com/c/gerrit/+/312633 can be reverted.
    // As explained in the commit message of this change doing this check is only possible if there
    // are no changes with gaps in patch set numbers. Since it's planned to fix-up old changes with
    // gaps in patch set numbers, this todo is a reminder to add back the check once this is done.
    return new AutoValue_ApprovalContext(
        psaKey, psaValue, patchSet, changeNotes, changeKind, isMerge, revWalk, repoConfig);
  }
}
