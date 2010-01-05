// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;
import static com.google.gerrit.reviewdb.ApprovalCategory.UPLOAD;


import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.server.CurrentUser;

import org.eclipse.jgit.lib.Constants;

import java.util.Collection;
import java.util.Set;


/**
 * Manages access control for Git refs.
 */
public class RefControl {

  private final ProjectControl projectControl;
  private final String refName;

  RefControl(final ProjectControl projectControl, final String refName) {
    this.projectControl = projectControl;
    this.refName = refName;
  }

  public String getRefName() {
    return refName;
  }

  /**
   * Determines whether the user can submit a change to the ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can submit a change to
   * the Git ref
   */
  public boolean canSubmit() {
    return canPerform(SUBMIT, (short) 1);
  }

  /**
   * Determines whether the user can upload a change to the ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can upload a change to
   * the Git ref
   */
  public boolean canUpload() {
    return canPerform(UPLOAD, (short) 1);
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @return {@code true} if the user specified can create a new Git ref
   */
  public boolean canCreate() {
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        if (projectControl.isOwner()) {
          return true;
        }
        if (isHead() && canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead() && canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
          return true;
        }
        if (isTag() && canPerform(PUSH_TAG, (short) 1)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  /**
   * Determines whether the user can delete the Git ref controlled by
   * this object.
   *
   * @return {@code true} if the user specified can delete a Git ref.
   */
  public boolean canDelete() {
    switch (getCurrentUser().getAccessPath()) {
      case WEB:
        if (projectControl.isOwner()) {
          return true;
        }
        if (isHead() && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      case SSH:
        if (isHead() && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
          return true;
        }
        return false;

      default:
        return false;
    }
  }

  public boolean canPerform(ApprovalCategory.Id actionId, short level) {
    boolean projectLevel = projectControl.canPerform(actionId, level);
    Collection<RefRight> refRights = getRefRights();
    final Set<AccountGroup.Id> groups = getCurrentUser().getEffectiveGroups();
    int val = Integer.MIN_VALUE;
    for (final RefRight right : refRights) {
      if (right.getApprovalCategoryId().equals(actionId)
          && groups.contains(right.getAccountGroupId())
          && matches(refName, right.getRefPattern())) {
        if (val < 0 && right.getMaxValue() > 0) {
          // If one of the user's groups had denied them access, but
          // this group grants them access, prefer the grant over
          // the denial. We have to break the tie somehow and we
          // prefer being "more open" to being "more closed".
          //
          val = right.getMaxValue();
        } else {
          // Otherwise we use the largest value we can get.
          //
          val = Math.max(right.getMaxValue(), val);
        }
      }
    }
    boolean refLevel = (val >= level);
    return projectLevel || refLevel;
  }

  private Collection<RefRight> getRefRights() {
    return projectControl.getProjectState().getRefRights();
  }

  private boolean isHead() {
    return refName.startsWith(Constants.R_HEADS);
  }

  private CurrentUser getCurrentUser() {
    return projectControl.getCurrentUser();
  }

  private boolean isTag() {
    return refName.startsWith(Constants.R_TAGS);
  }

  public static boolean matches(String refName, String refPattern) {
    if (refPattern.endsWith("/*")) {
      return refName.startsWith(
          refPattern.substring(0, refPattern.length() - 1));
    } else {
      return refName.equals(refPattern);
    }
  }
}
