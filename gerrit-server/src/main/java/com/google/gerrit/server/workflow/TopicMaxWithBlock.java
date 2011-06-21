// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.workflow;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ChangeSetApproval;

/**
 * Computes an {@link ApprovalCategory} by looking at maximum values.
 * <p>
 * In order to be considered "approved" this function requires that:
 * <ul>
 * <li>The maximum negative value is never used;</li>
 * <li>The maximum positive value is used at least once;</li>
 * <li>The user approving the maximum positive has been granted that.</li>
 * </ul>
 * <p>
 * This function is primarily useful for review fields, with values such as:
 * <ul>
 * <li>+2: Approved change.</li>
 * <li>+1: Looks ok, but get another approval from someone with more depth.</li>
 * <li>-1: Soft reject, it isn't a great change but its OK if approved.</li>
 * <li>-2: Rejected, must not be submitted.
 * </ul>
 * <p>
 * Note that projects using this function would typically want to assign out the
 * middle range (-1 .. +1) to almost everyone, so people can indicate how they
 * feel about a change, but the extremes of -2 and +2 should be reserved for the
 * project's long-term maintainers, those who are most familiar with its code.
 */
public class TopicMaxWithBlock extends TopicCategoryFunction {
  public static String NAME = "MaxWithBlock";

  @Override
  public void run(final ApprovalType at, final TopicFunctionState state) {
    boolean rejected = false;
    boolean passed = false;
    for (final ChangeSetApproval a : state.getApprovals(at)) {
      state.normalize(at, a);

      rejected |= at.isMaxNegative(a);
      passed |= at.isMaxPositive(a);
    }

    // The type must not have had its max negative (a forceful reject)
    // and must have at least one max positive (a full accept).
    //
    state.valid(at, !rejected && passed);
  }
}
