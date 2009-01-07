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
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.rpc.Common;

/**
 * Computes an {@link ApprovalCategory} by requiring all others to be valid.
 * <p>
 * In order to be considered "approved" this function requires that all approval
 * categories with a position >= 0 (that is any whose
 * {@link ApprovalCategory#isAction()} method returns false) is valid in the
 * state.
 * <p>
 * This is mostly useful for actions, like {@link ApprovalCategory#SUBMIT}.
 */
public class AllValid extends CategoryFunction {
  public static String NAME = "AllValid";

  @Override
  public void run(final ApprovalType at, final FunctionState state) {
    for (final ApprovalType t : Common.getGerritConfig().getApprovalTypes()) {
      if (!state.isValid(t)) {
        state.valid(at, false);
        return;
      }
    }
    state.valid(at, true);
  }
}
