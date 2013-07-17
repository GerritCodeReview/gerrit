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

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.List;

/** Extended commit entity with code review specific metadata. */
public class CodeReviewCommit extends RevCommit {
  static CodeReviewCommit error(final CommitMergeStatus s) {
    final CodeReviewCommit r = new CodeReviewCommit(ObjectId.zeroId());
    r.statusCode = s;
    return r;
  }

  /**
   * Unique key of the PatchSet entity from the code review system.
   * <p>
   * This value is only available on commits that have a PatchSet represented in
   * the code review system.
   */
  PatchSet.Id patchsetId;

  /** The change containing {@link #patchsetId} . */
  Change change;

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
  CommitMergeStatus statusCode;

  /** Commits which are missing ancestors of this commit. */
  List<CodeReviewCommit> missing;

  public CodeReviewCommit(final AnyObjectId id) {
    super(id);
  }

  void copyFrom(final CodeReviewCommit src) {
    patchsetId = src.patchsetId;
    change = src.change;
    originalOrder = src.originalOrder;
    statusCode = src.statusCode;
    missing = src.missing;
  }
}
