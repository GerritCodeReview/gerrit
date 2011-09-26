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
 * <li>The maximum positive value is used at least once;</li>
 * <li>The user approving the maximum positive has been granted that.</li>
 * </ul>
 * <p>
 * This function is primarily useful for advisory review fields.
 */
public class TopicMaxNoBlock extends TopicCategoryFunction {
  public static String NAME = "MaxNoBlock";

  @Override
  public void run(final ApprovalType at, final TopicFunctionState state) {
    boolean passed = false;
    for (final ChangeSetApproval a : state.getApprovals(at)) {
      state.normalize(at, a);

      passed |= at.isMaxPositive(a);
    }

    // The type must have at least one max positive (a full accept).
    //
    state.valid(at, passed);
  }
}
