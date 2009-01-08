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

package com.google.gerrit.client.workflow;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ProjectRight;

import java.util.HashMap;
import java.util.Map;

/** Function to control {@link ChangeApproval}s in an {@link ApprovalCategory}. */
public abstract class CategoryFunction {
  private static Map<String, CategoryFunction> all =
      new HashMap<String, CategoryFunction>();
  static {
    all.put(AllValid.NAME, new AllValid());
    all.put(MaxWithBlock.NAME, new MaxWithBlock());
  }

  /**
   * Locate a function by name.
   * 
   * @param functionName the function's unique name.
   * @return the function implementation; null if the function is not known to
   *         Gerrit and thus cannot be executed.
   */
  public static CategoryFunction forName(final String functionName) {
    return all.get(functionName);
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
  public abstract void run(ApprovalType at, FunctionState state);

  public boolean isValid(final Account.Id accountId, final ApprovalType at,
      final FunctionState state) {
    for (final ProjectRight pr : state.getAllRights(at)) {
      if (state.isMember(accountId, pr.getAccountGroupId())
          && (pr.getMinValue() < 0 || pr.getMaxValue() > 0)) {
        return true;
      }
    }
    return false;
  }
}
