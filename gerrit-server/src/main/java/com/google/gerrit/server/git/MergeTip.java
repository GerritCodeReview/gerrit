// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Class describing a merge tip during merge operation.
 */
public class MergeTip {
  public static MergeTip from(@Nullable CodeReviewCommit initial,
      Collection<CodeReviewCommit> toMerge) {
    if (initial == null) {
      return null;
    }
    return new MergeTip(initial, toMerge);
  }

  private CodeReviewCommit branchTip;
  private Map<String,String> mergeResults;

  /**
   * @param initial Tip before the merge operation.
   * @param toMerge List of CodeReview commits to be merged in merge operation.
   */
  private MergeTip(CodeReviewCommit initial,
      Collection<CodeReviewCommit> toMerge) {
    this.mergeResults = Maps.newHashMap();
    this.branchTip = initial;
    // Assume fast-forward merge until opposite is proven.
    for (CodeReviewCommit commit : toMerge) {
      mergeResults.put(commit.getName(), commit.getName());
    }
  }

  /**
   * Moves this MergeTip to newTip and appends mergeResult.
   *
   * @param newTip The new tip
   * @param mergedFrom The result of the merge of newTip
   */
  public void moveTipTo(CodeReviewCommit newTip, String mergedFrom) {
    branchTip = newTip;
    mergeResults.put(mergedFrom, newTip.getName());
  }

  /**
   * The merge results of all the merges of this merge operation.
   *
   * @return The merge results of the merge operation Map<<sha1 of the commit to
   *         be merged>, <sha1 of the merge result>>
   */
  public Map<String, String> getMergeResults() {
    return this.mergeResults;
  }

  /**
   *
   * @return The current tip of the current merge operation.
   */
  public CodeReviewCommit getCurrentTip() {
    return branchTip;
  }
}
