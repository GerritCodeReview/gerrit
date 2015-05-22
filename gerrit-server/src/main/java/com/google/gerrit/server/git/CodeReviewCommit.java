// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ChangeControl;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.util.List;

/** Extended commit entity with code review specific metadata. */
public class CodeReviewCommit extends RevCommit {
  public static RevWalk newRevWalk(Repository repo) {
    return new RevWalk(repo) {
      @Override
      protected RevCommit createCommit(AnyObjectId id) {
        return new CodeReviewCommit(id);
      }
    };
  }
  static CodeReviewCommit revisionGone(ChangeControl ctl) {
    return error(ctl, CommitMergeStatus.REVISION_GONE);
  }

  static CodeReviewCommit noPatchSet(ChangeControl ctl) {
    return error(ctl, CommitMergeStatus.NO_PATCH_SET);
  }

  /**
   * Create an error commit.
   * <p>
   * Should only be used for error statuses such that there is no possible
   * non-zero commit on which we could call {@link
   * #setStatusCode(CommitMergeStatus)}, enumerated in the methods above.
   *
   * @param ctl control for change that caused this error
   * @param CommitMergeStatus status
   * @return new commit instance
   */
  private static CodeReviewCommit error(ChangeControl ctl,
      CommitMergeStatus s) {
    CodeReviewCommit r = new CodeReviewCommit(ObjectId.zeroId());
    r.setControl(ctl);
    r.statusCode = s;
    return r;
  }

  /**
   * Unique key of the PatchSet entity from the code review system.
   * <p>
   * This value is only available on commits that have a PatchSet represented in
   * the code review system.
   */
  private PatchSet.Id patchsetId;

  /** Change control for the change owner. */
  private ChangeControl control;

  /**
   * Ordinal position of this commit within the submit queue.
   * <p>
   * Only valid if {@link #patchsetId} is not null.
   */
  int originalOrder;

  /**
   * The result status for this commit.
   * <p>
   * Only valid if {@link #patchsetId} is not null.
   */
  private CommitMergeStatus statusCode;

  /** Commits which are missing ancestors of this commit. */
  List<CodeReviewCommit> missing;

  public CodeReviewCommit(final AnyObjectId id) {
    super(id);
  }

  public ChangeNotes notes() {
    return getControl().getNotes();
  }

  public CommitMergeStatus getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(CommitMergeStatus statusCode) {
    this.statusCode = statusCode;
  }

  public PatchSet.Id getPatchsetId() {
    return patchsetId;
  }

  public void setPatchsetId(PatchSet.Id patchsetId) {
    this.patchsetId = patchsetId;
  }

  public void copyFrom(final CodeReviewCommit src) {
    control = src.control;
    patchsetId = src.patchsetId;
    originalOrder = src.originalOrder;
    statusCode = src.statusCode;
    missing = src.missing;
  }

  public Change change() {
    return getControl().getChange();
  }

  public ChangeControl getControl() {
    return control;
  }

  public void setControl(ChangeControl control) {
    this.control = control;
  }
}
