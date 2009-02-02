// Copyright 2008 Google Inc.
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

package com.google.gerrit.git;

import com.google.gerrit.client.reviewdb.PatchSet;

import org.spearce.jgit.lib.AnyObjectId;
import org.spearce.jgit.revwalk.RevCommit;

import java.util.List;

/** Extended commit entity with code review specific metadata. */
class CodeReviewCommit extends RevCommit {
  /**
   * Unique key of the PatchSet entity from the code review system.
   * <p>
   * This value is only available on commits that have a PatchSet represented in
   * the code review system and whose PatchSet is in the current submit queue.
   * Merge commits created during the merge or commits that aren't in the submit
   * queue will keep this member null.
   */
  PatchSet.Id patchsetId;

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

  CodeReviewCommit(final AnyObjectId id) {
    super(id);
  }
}
