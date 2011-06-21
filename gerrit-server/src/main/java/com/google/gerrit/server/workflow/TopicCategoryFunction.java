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
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.server.CurrentUser;

import java.util.HashMap;
import java.util.Map;

/** Function to control {@link ChangeSetApproval}s in an {@link ApprovalCategory}. */
public abstract class TopicCategoryFunction {
  private static Map<String, TopicCategoryFunction> all =
      new HashMap<String, TopicCategoryFunction>();
  static {
    all.put(MaxWithBlock.NAME, new TopicMaxWithBlock());
    all.put(MaxNoBlock.NAME, new TopicMaxNoBlock());
    all.put(NoOpFunction.NAME, new TopicNoOpFunction());
    all.put(NoBlock.NAME, new TopicNoBlock());
  }

  /**
   * Locate a function by category.
   *
   * @param category the category the function is for.
   * @return the function implementation; {@link NoOpFunction} if the function
   *         is not known to Gerrit and thus cannot be executed.
   */
  public static TopicCategoryFunction forCategory(final ApprovalCategory category) {
    final TopicCategoryFunction r = all.get(category.getFunctionName());
    return r != null ? r : new TopicNoOpFunction();
  }

  /**
   * Normalize ChangeApprovals and set the valid flag for this category.
   * <p>
   * Implementors should invoke:
   *
   * <pre>
   * state.valid(at, true);
   * </pre>
   * <p>
   * If the set of approvals from <code>state.getApprovals(at)</code> covers the
   * requirements for the function, indicating the category has been completed.
   * <p>
   * An example implementation which requires at least one positive and no
   * negatives might be:
   *
   * <pre>
   * boolean neg = false, pos = false;
   * for (final ChangeApproval ca : state.getApprovals(at)) {
   *   state.normalize(ca);
   *   neg |= ca.getValue() &lt; 0;
   *   pos |= ca.getValue() &gt; 0;
   * }
   * state.valid(at, !neg &amp;&amp; pos);
   * </pre>
   *
   * @param at the cached category description to process.
   * @param state state to read approvals and project rights from, and to update
   *        the valid status into.
   */
  public abstract void run(ApprovalType at, TopicFunctionState state);

  public boolean isValid(final CurrentUser user, final ApprovalType at,
      final TopicFunctionState state) {
    return !state.controlFor(user) //
        .getRange(Permission.forLabel(at.getCategory().getLabelName())) //
        .isEmpty();
  }
}
