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

package com.google.gerrit.server.workflow;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.RefControl;

/**
 * Computes if the submit function can be used.
 * <p>
 * In order to be considered "approved" this function requires that all approval
 * categories with a position >= 0 (that is any whose
 * {@link ApprovalCategory#isAction()} method returns false) is valid and that
 * the change state be {@link Change.Status#NEW}.
 * <p>
 * This is mostly useful for actions, like {@link ApprovalCategory#SUBMIT}.
 */
public class SubmitFunction extends CategoryFunction {
  public static String NAME = "Submit";

  @Override
  public void run(final ApprovalType at, final FunctionState state) {
    state.valid(at, valid(at, state));
  }

  @Override
  public boolean isValid(final CurrentUser user, final ApprovalType at,
      final FunctionState state) {
    if (valid(at, state)) {
      RefControl rc = state.controlFor(user);
      for (final RefRight pr : rc.getApplicableRights(at.getCategory().getId())) {
        if (user.getEffectiveGroups().contains(pr.getAccountGroupUUID())
            && pr.getMaxValue() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean valid(final ApprovalType at, final FunctionState state) {
    if (state.getChange().getStatus() != Change.Status.NEW) {
      return false;
    }
    for (final ApprovalType t : state.getApprovalTypes()) {
      if (!state.isValid(t)) {
        return false;
      }
    }
    return true;
  }
}
